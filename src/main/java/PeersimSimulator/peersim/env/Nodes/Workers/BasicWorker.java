package PeersimSimulator.peersim.env.Nodes.Workers;

import PeersimSimulator.peersim.config.FastConfig;
import PeersimSimulator.peersim.core.CommonState;
import PeersimSimulator.peersim.core.Linkable;
import PeersimSimulator.peersim.core.Network;
import PeersimSimulator.peersim.core.Node;
import PeersimSimulator.peersim.env.Nodes.Events.BasicOffloadInstructions;
import PeersimSimulator.peersim.env.Nodes.Events.OffloadInstructions;
import PeersimSimulator.peersim.env.Nodes.Events.TaskOffloadEvent;
import PeersimSimulator.peersim.env.Records.LoseTaskInfo;
import PeersimSimulator.peersim.env.Tasks.Application;
import PeersimSimulator.peersim.env.Tasks.ITask;
import PeersimSimulator.peersim.env.Tasks.TaskHistory;
import PeersimSimulator.peersim.env.Util.Log;
import PeersimSimulator.peersim.transport.Transport;

import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

public class BasicWorker extends AbstractWorker{
    public BasicWorker(String prefix) {
        super(prefix);
    }

    @Override
    public void nextCycle(Node node, int protocolID) {
        if (!active) return;

//        if(this.id == 1){
//            this.wrkErrLog("RAN THE CYCLE FOR 1", "RAN THE CYCLE FOR 1");
//        }

        if(this.getTotalNumberOfTasksInNode() >= this.qMAX){
            this.wrkErrLog("PROCESSING OVERLOADED", "Began processing task overloaded");
            this.timesOverloaded++;
        }

        // Advance Task processing and update status.
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
        if ((CommonState.getTime() % RANK_EVENT_DELAY) == 0 || this.awaitingSerialization() ) { // && !this.recievedApplications.isEmpty() // if offloaded a task and does not run this then no cleanup.
            applicationSerialization();
        }

        // Then Communicate changes in Queue Size and Recieved Nodes  to BasicController.
        if (this.changedWorkerState) { // TODO Guarantee we inform neighbours. Guarantee no double offloading.
            broadcastStateChanges(node, protocolID);
        }
        // Tally overload statistics.

    }

// TODO Was figuring out discrepancy between the number of unprocessed tasks and the toAdd value. This should be the key
//  to fixing the differences between Q and freeSlots.

    @Override
    protected void handleTaskOffloadEvent(TaskOffloadEvent ev) {



        if (this.getId() != ev.getDstNode()) {
            wrkErrLog(EVENT_OFFLOADED_TASKS_ARRIVED_AT_WRONG_NODE, " taskId=" + ev.getTask().getId() + " appId="+ev.getTask().getAppID()+" originalHandler=" + ev.getTask().getOriginalHandlerID() +" arrivedAt=" + this.getId() + " supposedToArriveAt=" + ev.getDstNode());
            return;
        }
        ITask offloadedTask = ev.getTask();
        offloadedTask.addEvent(TaskHistory.TaskEvenType.OFFLOADED, this.id, CommonState.getTime());
        wrkInfoLog(EVENT_TASK_OFFLOAD_RECIEVE, " taskId=" + ev.getTask().getId() + " appId="+ev.getTask().getAppID()+" originalHandler=" + ev.getTask().getOriginalHandlerID());
        if (this.getTotalNumberOfTasksInNode() >= qMAX) {
            this.droppedLastCycle++;
            this.totalDropped++;
            this.failedOnArrivalToNode++;
            Log.err("Dropping TasksHandle(" + this.droppedLastCycle + ") Node " + this.getId() + " is Overloaded!"); // TODO
        } else {
            LoseTaskInfo lti = ev.asLoseTaskInfo();
            double rank = 0;
            offloadedTask.setCurrentRank(rank);
            this.loseTaskInformation.put(offloadedTask.getId(), lti);
            this.queue.add(offloadedTask);
            this.tasksOffloadedToNode++;
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
                queue.add(t);
            }
        }
        this.resetReceived();
    }



    @Override
    protected boolean nextProcessableTask(Node node, int pid) {
        if (current != null && !current.done()) {
            return this.changedWorkerState;
        }
        if ((current == null || current.done()) && queue.isEmpty() && !recievedApplications.isEmpty()) {
            applicationSerialization();
        }
        this.current = this.selectNextAvailableTask(false);
        boolean taskAssigend = this.current != null;
        if(taskAssigend) {
            this.current.addEvent(TaskHistory.TaskEvenType.SELECTED_FOR_PROCESSING, this.id, CommonState.getTime());
            this.tasksToBeLocallyProcessed.remove(current.getId());
        }else{
            wrkInfoLog(EVENT_NO_TASK_PROCESS, "id="+this.getId());
        }
        return this.changedWorkerState;
    }

    private ITask selectNextAvailableTask(boolean offloading) {
        if (this.current != null && !offloading) {
            return this.current;
        }
        if(!offloading) {
            if (!this.queue.isEmpty()) {
                return this.queue.pollFirst();
            }
            if (!this.recievedApplications.isEmpty()) {
                applicationSerialization();
                return this.queue.pollFirst();
            }
            return null;
        }else{
            // In this branch we must be offloading stuff. So we will need to iterate over the options, check wether
            // they were picked to process locally. If not, send the first task that was not selected for local processing.
            // If all tasks were flagged for local processing, then we will return null.

            // We look over all the apps.
            if(!this.recievedApplications.isEmpty()) {
                applicationSerialization();
            }
            for (ITask task : this.queue) {
                if (!this.tasksToBeLocallyProcessed.contains(task.getId())) {
                    this.queue.remove(task);
                    return task;
                }
            }

            return null;
        }

    }

    private ITask selectNextAvailableTaskNoRemove(boolean offloading) {
        if (this.current != null && !offloading) {
                return this.current;
        }
        if (!this.queue.isEmpty()) {
        try{
            return this.queue.first();
        }catch (NoSuchElementException E){
            return null;
        }
        }
        if(!this.recievedApplications.isEmpty()){
            applicationSerialization();
            try {
                return this.queue.pollFirst();
            }catch(NoSuchElementException E){
                return null;
            }
        }
        return null;
    }

    @Override
    public boolean offloadInstructions(int pid, OffloadInstructions offloadInstructions) {
        if(this.awaitingSerialization()) applicationSerialization();
        BasicOffloadInstructions oi = (BasicOffloadInstructions) offloadInstructions;

        if (oi.getNeighbourIndex() != 0) {
            ITask task = this.selectNextAvailableTask(true);
            // ngl, it's late... There is for sure a better way of implementing this. This boolean overloading the method
            // does not look very good.
            if(task == null) {
                wrkInfoLog(EVENT_NO_TASK_OFFLOAD, "id="+this.getId());
                return false;
            }
            // TODO this is a problem, it works because I try to leave the self in position 0 in the linkable.
            //  It would not work with multiple SimulationManagers.

            // Self is always the first to be added to the linkable. And should not be changed.
/*
            if(this.queue.isEmpty() ){
                return false;
            }
*/
            Node node = Network.get(this.getId());
            int linkableID = FastConfig.getLinkable(pid);
            Linkable linkable = (Linkable) node.getProtocol(linkableID);
            if(!validTargetNeighbour(oi.getNeighbourIndex(), linkable)) {
                return false;
            }

            Node target = linkable.getNeighbor(oi.getNeighbourIndex());
            if ( !target.isUp()) {
                wrkErrLog(EVENT_ERR_NODE_OUT_OF_BOUNDS, "The requested target node is outside the nodes known by the DAGWorker="
                        + (oi.getNeighbourIndex() < 0 || oi.getNeighbourIndex() > linkable.degree())
                        + ". Or is down=" + target.isUp());
                return false;
            }

            LoseTaskInfo lti = getOrGenerateLoseTaskInfo(node, task);
            if (lti == null) {
                throw new RuntimeException("Something went wrong with tracking of lose tasks with loseTaskInfo. Killing the simulation.");
            }
            this.timesOffloadedFromNode++;
            wrkInfoLog("OFFLOADING TASK", "taskId=" + task.getId() + " appId=" + task.getAppID() + " originalHandler=" + task.getOriginalHandlerID() + " to=" + target.getID());
            ((Transport) node.getProtocol(FastConfig.getTransport(Worker.getPid()))).
                    send(
                            node,
                            target,
                            new TaskOffloadEvent(this.id, target.getIndex(), lti),
                            selectOffloadTargetPid(oi.getNeighbourIndex(), target)
                    );
            this.changedWorkerState = true;
            //this.current = null;
        }else{
            // oi.getNeighbourIndex() == this.getId()
            ITask task = this.selectNextAvailableTaskNoRemove(true);
            if (task != null) {
                this.tasksToBeLocallyProcessed.add(task.getId());
            }
        }
        return true;
    }
    void resetReceived() {
        recievedApplications = new LinkedList<>();
        toAddSize = 0;
    }
}
