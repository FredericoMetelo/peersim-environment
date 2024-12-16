package PeersimSimulator.peersim.env.Nodes.Workers;

import PeersimSimulator.peersim.config.FastConfig;
import PeersimSimulator.peersim.core.CommonState;
import PeersimSimulator.peersim.core.Linkable;
import PeersimSimulator.peersim.core.Network;
import PeersimSimulator.peersim.core.Node;
import PeersimSimulator.peersim.env.Nodes.Events.BatchOffloadInstructions;
import PeersimSimulator.peersim.env.Nodes.Events.OffloadInstructions;
import PeersimSimulator.peersim.env.Nodes.Events.TaskOffloadEvent;
import PeersimSimulator.peersim.env.Records.LoseTaskInfo;
import PeersimSimulator.peersim.env.Tasks.Application;
import PeersimSimulator.peersim.env.Tasks.ITask;
import PeersimSimulator.peersim.env.Tasks.TaskHistory;
import PeersimSimulator.peersim.env.Util.Log;
import PeersimSimulator.peersim.transport.Transport;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class BatchWorker extends AbstractWorker{

    public static final String EVENT_BATCH_SIZE_BIGGER_THAN_R = "BATCH SIZE BIGGER THAN R";

    // TODO this is a copy of basic worker, need to convert this to allow batch action
    public BatchWorker(String prefix) {
        super(prefix);
    }

    @Override
    public void nextCycle(Node node, int protocolID) {
        if (!active) return;

        if(this.getTotalNumberOfTasksInNode() >= qMAX){
            this.timesOverloaded++;
        }
        // Advance Task processinnd update status.
        double remainingProcessingPower = processingPower;
        while (remainingProcessingPower > 0 && !this.idle()) {
            if (this.current == null || this.current.done()) {
                changedWorkerState = nextProcessableTask(node, pid);
                if (current == null) {
                    break;
                }
            }
            remainingProcessingPower = current.addProgress(remainingProcessingPower);
            if (current.done()) {
                totalTasksProcessed++;
                handleTaskConcludedEvent(node, pid, current);
                current = null;
            }
        }
        this.addProcessingEnergyCost(processingPower - remainingProcessingPower);
        cleanExpiredApps();
        if (!this.hasController && ((CommonState.getTime() % RANK_EVENT_DELAY) == 0 || this.awaitingSerialization() )) { // && !this.recievedApplications.isEmpty() // if offloaded a task and does not run this then no cleanup.
            applicationSerialization();
        }

        // Then Communicate changes in Queue Size and Recieved Nodes  to BasicController.
        if (this.changedWorkerState) { // TODO Guarantee we inform neighbours. Guarantee no double offloading.
            broadcastStateChanges(node, protocolID);
        }


    }



    @Override
    protected void handleTaskOffloadEvent(TaskOffloadEvent ev) {
        if (this.getId() != ev.getDstNode()) {
            wrkErrLog(EVENT_OFFLOADED_TASKS_ARRIVED_AT_WRONG_NODE, " taskId=" + ev.getTask().getId() + " appId="+ev.getTask().getAppID()+" originalHandler=" + ev.getTask().getOriginalHandlerID() +" arrivedAt=" + this.getId() + " supposedToArriveAt=" + ev.getDstNode());
            return;
        }
        ITask offloadedTask = ev.getTask();
        offloadedTask.addEvent(TaskHistory.TaskEvenType.OFFLOADED, this.id, CommonState.getTime());
        wrkInfoLog(EVENT_TASK_OFFLOAD_RECIEVE, " taskId=" + ev.getTask().getId() + " appId="+ev.getTask().getAppID()+" originalHandler=" + ev.getTask().getOriginalHandlerID());
        if (this.getTotalNumberOfTasksInNode() > qMAX) {
            this.droppedLastCycle++;
            this.totalDropped++;
            this.failedOnArrivalToNode++;
            Log.err("Dropping Tasks(" + this.droppedLastCycle + ") Node " + this.getId() + " is Overloaded!"); // TODO
        } else {
            LoseTaskInfo lti = ev.asLoseTaskInfo();
            double rank = 0;
            offloadedTask.setCurrentRank(rank);
            this.loseTaskInformation.put(offloadedTask.getId(), lti);
            List<ITask> dummyExpandedDag =new LinkedList<>();
            dummyExpandedDag.add(offloadedTask);
            HashMap<String, ITask> dummyTasks = new HashMap<>();
            dummyTasks.put(offloadedTask.getId(), offloadedTask);

            Application dummyApp = new Application(
                    dummyTasks,
                    offloadedTask,
                    lti.getDeadline(),
                    offloadedTask.getClientID(),
                    dummyExpandedDag);
            this.recievedApplications.add(dummyApp);
        }
        this.changedWorkerState = true;
    }

    @Override
    protected void applicationSerialization() {
        if(recievedApplications.isEmpty()) return ;
        wrkInfoLog("SERIALIZING EVENT", "Q_size=" + this.queue.size() + " rcv_Apps=" + this.recievedApplications.size() + " working=" + !(current == null));
        for (Application app : recievedApplications) {
            List<ITask> tasks = app.expandToList();
            for (ITask t : tasks) {
                double rank = 0;
                t.setCurrentRank(rank);
                this.queue.add(t);
            }
        }
        resetReceived();
    }

    void resetReceived() {
        recievedApplications = new LinkedList<>();
        toAddSize = 0;
    }

    @Override
    protected boolean nextProcessableTask(Node node, int pid) {
        if (current != null && !current.done()) {
            return this.changedWorkerState;
        }
        // TODO confirm this is correct
        if (!this.hasController && ((current == null || current.done()) && queue.isEmpty() && !recievedApplications.isEmpty())) {
            applicationSerialization();
        }
        this.current = this.selectNextAvailableTask();
        boolean taskAssigend = this.current != null;
        if(taskAssigend) {
            this.current.addEvent(TaskHistory.TaskEvenType.SELECTED_FOR_PROCESSING, this.id, CommonState.getTime());
            this.tasksToBeLocallyProcessed.remove(current.getId());
        }else{
            wrkInfoLog(EVENT_NO_TASK_PROCESS, "id="+this.getId());
        }
        return this.changedWorkerState;
    }


    private ITask selectNextAvailableTask() {
        if (this.current != null) {
            return this.current;
        }
        if (!this.queue.isEmpty()) {
            return this.queue.pollFirst();
        }
        if(!this.hasController && !this.recievedApplications.isEmpty()){
            applicationSerialization();
            return this.queue.pollFirst();
        }
        return null;
    }


    /**
     * This method will offlaod the tasks, in the case of batch actions, the tasks will be offloaded in the order they
     * have in the received actions. For example if we have [t1, t2, t3, t4] in the received actions, and we have the
     * instructions to offload to [1, 2, 3] then t1 will be offloaded to 1, t2 to 2, t3 to 3 and t4 will remain in the
     * received queue.
     * @param pid
     * @param offloadInstructions
     * @return whether all task offlaodings succeeded.
     */
    @Override
    public boolean offloadInstructions(int pid, OffloadInstructions offloadInstructions) {
        BatchOffloadInstructions oi = (BatchOffloadInstructions) offloadInstructions;

        if(this.recievedApplications.isEmpty() || this.recievedApplications.size() < oi.neighbourIndexes().size()){
            wrkInfoLog(EVENT_BATCH_SIZE_BIGGER_THAN_R, "id="+this.getId());
            return false;
        }

        boolean success = true;
        Node node = Network.get(this.getId());
        int linkableID = FastConfig.getLinkable(pid);
        Linkable linkable = (Linkable) node.getProtocol(linkableID);

        int indexInRemainingTasks = 0;
        Iterator<Application> appIter = this.recievedApplications.iterator();
        while(appIter.hasNext() && indexInRemainingTasks < oi.neighbourIndexes().size()) {
            Application app = appIter.next();
            int neighbourIndex = oi.neighbourIndexes().get(indexInRemainingTasks);
            ITask task = app.expandToList().get(0); // Only works with dependencyless tasks.

            if (neighbourIndex != 0) {
                if(!validTargetNeighbour(neighbourIndex, linkable)) {
                    success = false;
                    indexInRemainingTasks++;
                    continue;
                }

                Node target = linkable.getNeighbor(neighbourIndex);
                if ( !target.isUp()) {
                    wrkErrLog(EVENT_ERR_NODE_OUT_OF_BOUNDS, "The requested target node is outside the nodes known by the DAGWorker="
                            + (neighbourIndex < 0 || neighbourIndex > linkable.degree())
                            + ". Or is down=" + target.isUp());
                    success = false;
                }

                LoseTaskInfo lti = getOrGenerateLoseTaskInfo(node, task);
                if (lti == null) {
                    throw new RuntimeException("Something went wrong with tracking of lose tasks with loseTaskInfo. Killing the simulation.");
                }

                wrkInfoLog("OFFLOADING TASK", "taskId=" + task.getId() + " appId=" + task.getAppID() + " originalHandler=" + task.getOriginalHandlerID() + " to=" + target.getID());
                ((Transport) node.getProtocol(FastConfig.getTransport(Worker.getPid()))).
                        send(
                                node,
                                target,
                                new TaskOffloadEvent(this.id, target.getIndex(), lti),
                                selectOffloadTargetPid(neighbourIndex, target)
                        );

                appIter.remove();
                this.changedWorkerState = true;
            }else{
                // oi.getNeighbourIndex() == this.getId()
                this.tasksToBeLocallyProcessed.add(task.getId());
                this.queue.add(task);
            }
            indexInRemainingTasks++;
        }
        return success;
    }

    @Override
    public int getTotalNumberOfTasksInNode(){
        return this.queue.size() + this.recievedApplications.size() + (this.current == null ? 0 : 1);
    }
}
