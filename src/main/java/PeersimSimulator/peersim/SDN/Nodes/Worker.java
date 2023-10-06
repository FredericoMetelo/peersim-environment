package PeersimSimulator.peersim.SDN.Nodes;

import PeersimSimulator.peersim.SDN.Tasks.Application;
import PeersimSimulator.peersim.SDN.Tasks.ITask;
import PeersimSimulator.peersim.SDN.Util.Log;
import PeersimSimulator.peersim.SDN.Nodes.Events.*;
import java.util.*;

import PeersimSimulator.peersim.cdsim.CDProtocol;
import PeersimSimulator.peersim.edsim.EDProtocol;
import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.config.FastConfig;
import PeersimSimulator.peersim.core.*;
import PeersimSimulator.peersim.transport.Transport;


public class Worker implements CDProtocol, EDProtocol {
    //======================================================================================================
    // Constants/Immutable values
    //======================================================================================================
    private static final String PAR_NAME = "name";


    /**
     * Represents the frequency of the CPUs on this machine.
     */
    public final double CPU_FREQ;
    private static final String PAR_CPU_FREQ = "FREQ";
    public final int CPU_NO_CORES;
    private static final String PAR_CPU_NO_CORES = "NO_CORES";
    public final int Q_MAX;
    private static final String PAR_Q_MAX = "Q_MAX";
    //======================================================================================================
    // Variables
    //======================================================================================================
    /**
     * The node internal id
     */
    private int id;
    /** Protocol identifier, obtained from config property {@link #PAR_NAME}. */
    private static int pid;
    /**
     * Number of instructions the CPU can compute in one timestep. This is a gross simplification of the processing power of the CPU.
     * We ignore important factors like the memory management and the influence of the OS.
     */
    public double processingPower;

    /**
     * Tasks dropped on the last cycle.
     */
    public int droppedLastCycle;

    /**
     * Total tasks dropped.
     */
    public int totalDropped;

    public int totalTasksRecieved;
    public int tasksRecievedSinceLastCycle;
    public int totalTasksProcessed;

    public int totalTasksOffloaded;

    /**
     * Queue with the requests assigned to this Node.
     * This represents the Q_i.
     */
    Queue<ITask> queue; // TODO Convert this to either an heap or another form of ordered list...

    /**
     * Requests that arrived at this node and are awaiting processing.
     */
    List<Application> recievedApplications;

    Map<String, Application> managedApplications;


    ITask current;
    /**
     * Flag for if there have been changes to the queue size o new requests received.
     */
    private boolean changed;


    /**
     * Defines f this node is working as a Worker
     * false - is not; true - is a Worker
     */
    private boolean active;

    // invariant: totalTasksReceived = totalTasksProcessed + totalTasksDropped + totalTasksOffloaded + getQueueSize()

    public Worker(String prefix) {
        //======== Define the Constant values from Configs ===========//
        // TODO make this definable from the configs.
        // TODO in future have multiple classes of nodes?
        pid = Configuration.getPid(prefix + "."+PAR_NAME);
        CPU_FREQ = Configuration.getDouble( prefix + "." + PAR_CPU_FREQ, 1e7);
        CPU_NO_CORES = Configuration.getInt( prefix + "." + PAR_CPU_NO_CORES, 4);
        Q_MAX = Configuration.getInt(prefix + "." + PAR_Q_MAX , 10);

        printParams();
        processingPower = Math.floor(CPU_NO_CORES * CPU_FREQ);
        //======== Init Datastructures ===========//
        queue = new LinkedList<>(); // Assuming no concurrency within node. If we want to handle multiple requests confirm trx safe.
        recievedApplications = new LinkedList<>();
        current  = null;
        active = true;
        totalDropped = 0;
        droppedLastCycle = 0;
        totalTasksRecieved = 0;
        tasksRecievedSinceLastCycle = 0;
        totalTasksProcessed = 0;
        totalTasksOffloaded = 0;
    }

    @Override
    public Object clone() {
        Worker svh=null;
        try {
            svh=(Worker)super.clone();
            svh.resetQueue();
        }
        catch( CloneNotSupportedException e ) {} // never happens
        return svh;
    }



    //======================================================================================================
    // Methods
    //======================================================================================================
    @Override
    public void nextCycle(PeersimSimulator.peersim.core.Node node, int protocolID) {
        if(!active) return;
        // Advance Task processing and update status.
        /* Behaviour of the node.
        *  1. A task has a number of instructions. The CPU has 4 cores and a frequency, all CPUs the same frequency.
        *    the amount of task per time step is no_instr / (cores * frequency)
        *  2. Tasks are executed from the beginning of queue by order. If one task is finished, in a timestep all the remaining instructions of that cycle will be used for the next task.
        *  3. If a queue is empty and there are tasks in the recievedRequests those are processed
        *  4. If the there are no tasks to be processed the node idles.
        */
        // droppedLastCycle = 0;
        double remain = processingPower;
        while (remain > 0 && !idle()){
            if(current == null || current.done()){
                changed = selectNextTask();
                if(current == null){
                    break;
                }
            }
            remain = current.addProgress(remain);
            if(current.done()){
                totalTasksProcessed++;

                if(this.id == current.getOriginalHandlerID()){
                    // Add to application progress
                    Application app = this.managedApplications.get(current.getAppID());
                    app.addProgress(current.getId());
                    if(app.isFinished()){
                        this.handleApplicationFinish(node, protocolID, app);
                    }

                }else if(this.id != current.getOriginalHandlerID()){ // I just like my ligatures... Leave me alone...
                    int linkableID = FastConfig.getLinkable(protocolID);
                    Linkable linkable = (Linkable) node.getProtocol(linkableID);
                    Node handler = linkable.getNeighbor(current.getOriginalHandlerID());

                    if(!handler.isUp()) return; // This happens task progress is lost.
                    Log.info("|WRK| TASK FINISH: SRC<"+ this.getId() + "> Task <" +this.current.getId()+ ">");
                    ((Transport)handler.getProtocol(FastConfig.getTransport(Client.getPid()))).
                            send(
                                    node,
                                    handler,
                                    new TaskConcludedEvent(this.id, current.getAppID(), current.getClientID()),
                                    Client.getPid()
                            );
                }
                }

        }

        // Then Communicate changes in Queue Size and Recieved Nodes  to Controller.
        if(this.changed){
            int linkableID = FastConfig.getLinkable(protocolID);
            Linkable linkable = (Linkable) node.getProtocol(linkableID);
            Node controller = linkable.getNeighbor(0);

            // Quoting the tutorial:
            // XXX quick and dirty handling of failures
            // (message would be lost anyway, we save time)
            if(!controller.isUp()) return;

            // Controller controllerProtocol = (Controller) controller.getProtocol(Controller.getPid());
            Log.info("|WRK| WORKER-INFO SEND: SRC<"+ this.getId() + "> Qi<" +this.queue.size()+ "> Wi <" + this.recievedApplications.size() + "> Working: <"  + !(current == null) + ">");

            ((Transport)controller.getProtocol(FastConfig.getTransport(Controller.getPid()))).
                    send(
                            node,
                            controller,
                            new WorkerInfo(this.id, this.queue.size(), this.recievedApplications.size(), averageTaskSize(), processingPower),
                            Controller.getPid()
                    );
            this.changed = false;
        }


    }

    private void handleApplicationFinish(PeersimSimulator.peersim.core.Node node, int protocolID, Application app){
    // Send answer to client if app finished!
        // Remove app from local structures
        managedApplications.remove(app.getAppID()); // If this is running then everything has been removed from the application.


        // Send results to client
        int linkableID = FastConfig.getLinkable(protocolID);
        Linkable linkable = (Linkable) node.getProtocol(linkableID);
        Node client = linkable.getNeighbor(app.getClientID());

        if(!client.isUp()) return; // This happens task progress is lost.
        Log.info("|WRK| TASK FINISH: SRC<"+ this.getId() + "> Task <" +this.current.getId()+ ">");
        ((Transport)client.getProtocol(FastConfig.getTransport(Client.getPid()))).
                send(
                        node,
                        client,
                        new AppConcludedEvent(this.id, app.getAppID(), app.getOutputDataSize()),
                        Client.getPid()
                );
    }

    private void applicationSerialization(){
        // Heap listNew = new Heap...
        // Compute whatever necessary metrics
        // Cycle through queued tasks ranking them and re-inserting them in app
        // Cycle through new applications ranking their every-tasks and re-inserting them in app
    }

    private int taskRank(Application app, ITask task, double minCompLoad, double maxCompLoad, int minSucc, int maxSucc){
    // Compute Parts of the ranking function
        // Normalized Number of Successors
        int En = app.getSuccessors().get(task.getId()).size();
        int normalized_En = (En - minSucc)/(maxSucc - minSucc);
        // Normalized Computational Load
        double Ln = task.getTotalInstructions();
        double normalized_Ln = (Ln - minCompLoad)/(maxCompLoad - minCompLoad);
        // Normalized Number of Occurrences (IGNORED)
        // Normalized Urgency Metric
        int urgency = -app.getDeadline() + CommonState.getIntTime() - app.getArrivalTime() + 1;
        // Normalized Completion Rate

    // Compute the ranking function

        return -1;
    }

    /**
     * This method computes the average task size.
     * @return the average number of instructions of the tasks in a node.
     */
    private double averageTaskSize() {
        double acc = this.current == null ? 0 : this.current.getTotalInstructions() - this.current.getProgress();
        double noTasks = this.current == null ? 0 : 1;
        for (ITask t: queue) {
            acc += t.getTotalInstructions();
            noTasks++;
        }
        for (ITask t: recievedApplications){
            acc += t.getTotalInstructions();
            noTasks++;
        }
        return (noTasks== 0) ? 0 : acc/noTasks; // note: rounds down
    }


    @Override
    public void processEvent(PeersimSimulator.peersim.core.Node node, int pid, Object event) {
        if(!active) return;
        if(event instanceof TaskOffloadEvent ev){
            // Receive Tasks Event Receives a set of new Tasks
            // The new tasks are added to the receivedRequests. This
            // is reasonable because this requests can be consumed for
            // processing as well.
            if(this.getId() != ev.getDstNode()){
                Log.info("|WRK| Offloaded Tasks arrived at wrong node...");
                return;
            }
            List<ITask> offloadedTasks = ev.getTaskList().stream().peek((t)->t.setClientID(this.id)).toList();
            Log.info("|WRK| TASK OFFLOAD RECIEVE: SRC<"+ ev.getSrcNode() + "> TARGET<"+this.getId()+"> NO_TASKS<" +offloadedTasks.size()+ ">");
            totalTasksRecieved += offloadedTasks.size();
            tasksRecievedSinceLastCycle += offloadedTasks.size();

            if(this.getNumberOfTasks() + offloadedTasks.size() >= Q_MAX) {
                for (int i = 0; i < offloadedTasks.size(); i++) {
                    if(this.getNumberOfTasks() >= Q_MAX) {
                        int dropped = offloadedTasks.size() - i;
                        this.droppedLastCycle += dropped;
                        this.totalDropped += dropped;
                        break;
                    }
                    this.queue.add(offloadedTasks.get(i));
                }
                Log.err("Dropping Tasks("+this.droppedLastCycle+") Node "+this.getId()+" is Overloaded!");

            }else {
                this.recievedApplications.addAll(offloadedTasks);
            }

            this.changed = true;

        }else if(event instanceof OffloadInstructions ev){
            // Offload Tasks Event Executes Offloading of data
            int noToOffload;
            if(ev.getNoTasks() <= this.recievedApplications.size())
                noToOffload = ev.getNoTasks();
            else{
                // Guarantee that we don't try to send more tasks than we actually can.
                Log.err("Tried to offload more tasks than are available.");
                // noToOffload = 0;
                // TODO Changed behaviour from offloading 0 to all the tasks available to offload. Yes, this could be
                //  optimized by setting the recievedRequests to as the moveTasks and creating a new recievedRequests.
                 noToOffload = recievedApplications.size();
            }
            int targetNode = ev.getTargetNode();

            List<ITask> moveTasks = new ArrayList<>(noToOffload);
            Log.info("|WRK| TASK OFFLOAD SEND: SRC<"+ this.id + "> TARGET<" + targetNode + "> NO_TASKS<" + noToOffload+ ">");
            int end = this.recievedApplications.size();
            for(int i = 0; i < noToOffload; i++){
                int index = end - 1 - i;
                moveTasks.add(i, this.recievedApplications.remove(index));
            }
            end = this.recievedApplications.size();
            for(int j = 0; j < end; j ++){
                // Add whats left of the received requests to the queue.
                this.queue.add(this.recievedApplications.remove(0));
            }

            totalTasksOffloaded += moveTasks.size();

            // Send list to node
            int linkableID = FastConfig.getLinkable(pid);
            Linkable linkable = (Linkable) node.getProtocol(linkableID);
            // As everyone knows each other this is should not be problematic.
            Node target = linkable.getNeighbor(targetNode);

            // Quoting the tutorial:
            // XXX quick and dirty handling of failures
            // (message would be lost anyway, we save time)
            if(!target.isUp()){
                // Send failed returning tasks.
                this.recievedApplications.addAll(moveTasks);
                return;
            }
// TODO
            moveTasks = moveTasks.stream().peek((t) -> t.setOriginalHandlerID(targetNode)).toList();
            ((Transport)target.getProtocol(FastConfig.getTransport(Worker.getPid()))).
                    send(
                            node,
                            target,
                            new TaskOffloadEvent(this.id, targetNode, moveTasks),
                            Worker.getPid()
                    );
            this.changed = true;

        } else if(event instanceof NewApplicationEvent ev){

            Log.info("|WRK| NEW APP RECIEVED: ID<"+this.getId()+"> APP_ID<" + ev.getAppID() +">");
            Application app = ev.getApp();

            this.totalTasksRecieved++;
            this.tasksRecievedSinceLastCycle++;

            if(managedApplications.keySet().size() >= Q_MAX){
                droppedLastCycle ++;
                totalDropped++;
                Log.err("Dropping Tasks, Node "+this.getId()+" is overloaded!");
                return;
            }

            Log.info("|WRK| NEW APP RECIEVE: ID<"+this.getId()+">TASK_ID<" + app.getAppID() +">");
            app.setHandlerID(this.id);
            app.setArrivalTime(CommonState.getIntTime());
            this.managedApplications.put(app.getAppID(), app);
            this.recievedApplications.add(app);
            this.changed = true;
        }

        // Note: Updates internal state only sends data to user later
    }





    public boolean isActive() {
        return active;
    }
    public int getNumberOfTasks(){
        return this.queue.size() + this.recievedApplications.size() + ((current == null || current.done()) ? 0 : 1);
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public static int getPid(){
        return pid;
    }
    private boolean idle() {
        return this.queue.isEmpty() && this.recievedApplications.isEmpty(); //  && (this.current == null || this.current.done())
    }

    public double getProcessingPower() {
        return processingPower;
    }

    public void setProcessingPower(double processingPower) {
        this.processingPower = processingPower;
    }

    /**
     * @return The number of dropped tasks since the last time this method was called.
     */
    public int getDroppedLastCycle() {
        int aux_droppedLastCycle = droppedLastCycle;
        droppedLastCycle = 0;
        return aux_droppedLastCycle;
    }

    /**
     * @return all the tasks dropped in this node since the beginning of the simulation.
     */
    public int getTotalDropped() {
        return totalDropped;
    }

    public int getTotalTasksRecieved() {
        return totalTasksRecieved;
    }

    /**
     * @return all the tasks received since the last time this method was called. Independently of how they were handled.
     */
    public int getTasksRecievedSinceLastCycle() {
        int aux_tasksRecievedSinceLastCycle = tasksRecievedSinceLastCycle;
        tasksRecievedSinceLastCycle = 0;
        return aux_tasksRecievedSinceLastCycle;
    }

    public int getTotalTasksProcessed() {
        return totalTasksProcessed;
    }

    public int getTotalTasksOffloaded() {
        return totalTasksOffloaded;
    }

    //======================================================================================================
    // Private Methods
    //======================================================================================================

    /**
     * This method selects the task to be processed next.
     * by order of priority sets current as: the current task if not finished -> the oldest task in queue -> the oldest task in receivedRequests
     *         -> null, if there are no tasks in the node.
     */
    public boolean selectNextTask() {
        if(current != null && !current.done()){
            // Finish ongoing task. No changes to current.

            return this.changed;
        }else if(!queue.isEmpty()){
            // Get next process in the awaiting queue.
            current = queue.poll();

            return true;
        } else if (!recievedApplications.isEmpty()) {
            // When the queue is empty it dips into the undistributed requests.
            current = recievedApplications.remove(0);

            return true;
        }
        // Node has no tasks therefore will idle.
        current = null;
        return this.changed;
    }



    private void resetQueue(){
        queue = new LinkedList<>(); // Assuming no concurrency within node. If we want to handle multiple requests confirm trx safe.
        recievedApplications = new LinkedList<>();
        current  = null;
    }

    @Override
    public String toString(){
        String curr = (current != null)? current.getId() : "NULL";
        return "Worker ID<" + this.getId() + "> | Q: " + this.queue.size() +" W: " + this.recievedApplications.size() + " Current: " + curr;
    }

    private void printParams(){
        //if(active)
            Log.dbg("Worker Params: NO_CORES<" + this.CPU_NO_CORES + "> FREQ<" + this.CPU_FREQ+ "> Q_MAX<"+ this.Q_MAX+">" );
    }



}
