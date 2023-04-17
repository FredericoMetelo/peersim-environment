package PeersimSimulator.peersim.SDN.Nodes;

import PeersimSimulator.peersim.SDN.Links.SDNNodeProperties;
import PeersimSimulator.peersim.SDN.Records.DebugInfo;
import PeersimSimulator.peersim.SDN.Util.Log;
import PeersimSimulator.peersim.SDN.Nodes.Events.OffloadInstructions;
import PeersimSimulator.peersim.SDN.Nodes.Events.WorkerInfo;
import PeersimSimulator.peersim.SDN.Records.Action;
import PeersimSimulator.peersim.SDN.Records.EnvState;
import PeersimSimulator.peersim.cdsim.CDProtocol;
import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.config.FastConfig;
import PeersimSimulator.peersim.core.Linkable;
import PeersimSimulator.peersim.core.Network;
import PeersimSimulator.peersim.core.Node;
import PeersimSimulator.peersim.edsim.EDProtocol;
import PeersimSimulator.peersim.transport.Transport;

import java.util.*;


public class Controller implements CDProtocol, EDProtocol {
    private static final String PAR_NAME = "name";

    private final int UTILITY_REWARD;
    private final static String PAR_UTILITY_REWARD = "r_u";

    private final int DELAY_WEIGHT;
    private final static String PAR_DELAY_WEIGHT = "X_d";

    private final int WRONG_MOVE_PUNISHMENT;
    private final int OVERLOAD_WEIGHT;
    private final static String PAR_OVERLOAD_WEIGHT = "X_o";

    public final int CYCLE_SIZE;
    private final static String PAR_CYCLE = "CYCLE";

    public final double EXPECTED_TASK_ARRIVAL_RATE;

    /**
     * The Normalized Thermal Noise power is measured in dBm/Hz. From "https://noisewave.com/faq.pdf"
     *
     * > Noise Figure is defined as the ratio of the signal-to-noise power at the input to the signal-to-noise power
     * > at the output of a device, in other words the degradation of the signal-to-noise ratio as the signal passes
     * > through the device. Since the input noise level is usually thermal noise from the source the convention is
     * > to adopt a reference temperature of 290º K. The noise figure becomes the ratio of the total noise
     * > power output to that portion of the noise power output due to noise at input when the source is 290º
     * > K. ...
     * The thermal noise power  at 290º K is following the same sources is constant and equal to -174 dBm/Hz
     */
    public final static int NORMALIZED_THERMAL_NOISE_POWER = 174;

    private static final int CONTROLLER_ID = 0;
    private static int pid;

    /**
     * Defines f this node is working as a Controller
     * False - is not; True - is a Controller
     */
    private boolean active;

    /**
     * selectedNode represents the latest selected ID of node, between [0, N]
     * <p>
     * The selection is based on an Evnet Queue, the node with the oldest event is selected. All other events of that
     * node are removed from the Queue.
     */
    int selectedNode;
    int cycle;
    List<WorkerInfo> workerInfo;
    private int id;

    private LinkedList<Integer> nodeUpdateEventList;

    /**
     * up and stop are the variables responsible for the flow management of the application.
     * up - represents the state of the application, if the application is running up will be true and the controller will accept requests from the agent.tap:
     * stop - is the variable that says if the application is executing and can't retrieve data. Every 100 ticks the simulation checks if the event queue is not empty.
     * And selects the next node to offload from.
     */
    private volatile boolean stop, up;
    public Controller(String prefix) {
        pid = Configuration.getPid(prefix+ "."+PAR_NAME);
        workerInfo = new ArrayList<>();
        active = false;
        up = false;
        stop = true;
        // Read Constants
        DELAY_WEIGHT = Configuration.getInt( prefix + "." + PAR_DELAY_WEIGHT, 1) ;
        OVERLOAD_WEIGHT = Configuration.getInt( prefix + "." + PAR_OVERLOAD_WEIGHT, 150);
        UTILITY_REWARD = Configuration.getInt( prefix + "." + PAR_UTILITY_REWARD, 1);
        CYCLE_SIZE = Configuration.getInt(PAR_CYCLE, 100);
        EXPECTED_TASK_ARRIVAL_RATE =  Configuration.getDouble( prefix + "." + Client.PAR_TASKARRIVALRATE, Client.DEFAULT_TASKARRIVALRATE );

        WRONG_MOVE_PUNISHMENT = -200*UTILITY_REWARD;
        selectedNode = 1; // Ignores the controller.
        cycle = 0; // Will transverse the available nodes.
        nodeUpdateEventList = new LinkedList<>();
        printParams();

    }

    @Override
    public Object clone() {
        Controller svh=null;
        try {
            svh=(Controller)super.clone();
            svh.workerInfo = new ArrayList<>();
        }
        catch( CloneNotSupportedException e ) {} // never happens
        return svh;
    }

    @Override
    public void nextCycle(Node node, int protocolID) {
        if(!active) return;
        if(workerInfo.isEmpty()) initializeWorkerInfo(node, Controller.getPid());
        // stop = true;
        // The simulation only stops if the queue is not empty. Otherwise, it continues for another 100 ticks.
        int linkableID = FastConfig.getLinkable(Controller.getPid());
        Linkable linkable = (Linkable) node.getProtocol(linkableID);
        stop = selectNextNode(linkable);
        up = true;
        awaitAction();
    }



    public double sendAction(Action a) {
        if(!active || a == null || a.noTasks() < 0 || a.nodeId() < 0) return WRONG_MOVE_PUNISHMENT - 10;

        // Pick Node to be offloaded. Inform Python   of the WorkerInfo in question. Get the Action for that node to execute.
        /*
         * This will initially be done in round-robin style across all the nodes except the controller.
         * Updating one node per cycle.
         */
        Node node = Network.get(CONTROLLER_ID);
        if(workerInfo.isEmpty()) initializeWorkerInfo(node, Controller.getPid());

        int linkableID = FastConfig.getLinkable(Controller.getPid());
        Linkable linkable = (Linkable) node.getProtocol(linkableID);
        if (linkable.degree() > 0) {

            Node selectedWorker = linkable.getNeighbor(selectedNode);
            Worker protocol = (Worker) selectedWorker.getProtocol(Worker.getPid());

            if(!selectedWorker.isUp() || workerInfo.isEmpty()) return WRONG_MOVE_PUNISHMENT - 1;

            int targetNode = a.nodeId();
            int noTasks = a.noTasks();
            if(targetNode <= 0 || targetNode - 1 > this.workerInfo.size()) {
                // Note: This was >= this.WorkerInfo.size -> Now Worker info is between 1 and SIZE - 1.
                // In a Network with 5 nodes, 0 is the controller then we have 1,2,3,4 as the Workers:
                // WorkerInfo: [1 2 3 4] where the size of the worker info is the index of the last node.

                Log.info("|CTR| SEND ACTION: ILEGAL -> The target node <"+targetNode+"> is outside the know node indexes!");
                // allow progress
                stop = false;
                return WRONG_MOVE_PUNISHMENT - 2; // - 1 is just to identify where the negative reward is happening.
            }
            // No checks for validity of source node, logic of program guarantee correct
            if(noTasks < 0 || noTasks > Objects.requireNonNull(getWorkerInfo(selectedNode)).getQueueSize()){
                // When the offload instructions request to offload more tasks than are available I decided to return the negative of the utility constant.
                // Note to self: This might be problematic as the number of tasks the node views is not ther real number
                // of tasks the actual node has. There is a delay on the information, this might one day become a bottleneck.
                // ¯\_(ツ)_/¯

                Log.info("|CTR| SEND ACTION: ILEGAL -> The target node <"+targetNode+"> can't offload that many tasks <"+noTasks+">!");
                // allow progress
                stop = false;
                return WRONG_MOVE_PUNISHMENT - 3;
            }

            // Regular offload behaviour
            Log.info("|CTR| SEND ACTION: SRC<" + this.getId() + "> to TARGET:<" +targetNode + "> offload (" + noTasks + ")");

            double reward = calculatReward(linkable, selectedWorker, targetNode, noTasks);

            ((Transport)selectedWorker.getProtocol(FastConfig.getTransport(Worker.getPid()))).
                    send(
                            node,
                            selectedWorker,
                            new OffloadInstructions(targetNode, noTasks), // TODO get the values for this constructor from python!!!
                            Worker.getPid()
                    );

            // allow progress
            stop = false;
            // [0, 1, 2]
            return reward;
        }
        return WRONG_MOVE_PUNISHMENT - 4;

    }



    private WorkerInfo getWorkerInfo(int nodeId){
        for (WorkerInfo w: workerInfo) {
            if(w.getId() == nodeId)
                return w;
        }
        return null;
    }


    @Override
    public void processEvent(Node node, int pid, Object event) {
        if(!active) return;
        // Recieve Update from Node.
        if(event instanceof WorkerInfo wi){
            updateNode(wi);
        }
    }



    //======================================================================================================
    // Private Methods
    //======================================================================================================

    //=== Node Management
    /**
     * Spin waits for the next action.
     */
    private void awaitAction() {
        Log.dbg("Start Waiting");
        while (stop) Thread.onSpinWait();
    }

    /**
     * Returns <code>true</code> if a node was selected, the selected node is stored in the <code>selectedNode</code> field. If no nodes have tasks
     * available to be offloaded then the method returns <code>false</code>.
     * @param linkable
     * @return <code>true</code> if a suitable node was found. <code>false</code> otherwise
     */
    private boolean selectNextNode(Linkable linkable) {
        // cycle = (cycle + 1) % (linkable.degree()-1);
        // selectedNode = 1 + cycle;
        if(nodeUpdateEventList.isEmpty()){
            return false;
        }
        this.selectedNode = -1;
        int nodeID;
        while(!nodeUpdateEventList.isEmpty()){
            nodeID = nodeUpdateEventList.removeFirst();
            // I exchanged the test from queuesize which would allow for w_i == 0, to the w_i itself.
            // this makes sense as the Q is basically the size of the waiting nodes + the ones being processed,
            // the nodes being processed can't be offloaded so it only makes sense to only stop if a node has stuff that
            // could  be offloaded.
            if(Objects.requireNonNull(getWorkerInfo(nodeID)).getW_i() > 0){
                this.selectedNode = nodeID;
                this.nodeUpdateEventList.removeAll(Collections.singleton(nodeID));
                // If it has tasks available and is going to be processed no need
                // to have more entries of the node in the queue.
                return true;
            }
        }
        return false;
    }
    private void updateNode(WorkerInfo newWi){
        for (WorkerInfo oldWi : workerInfo) {
            if(oldWi.getId() == newWi.getId()){
                Log.info("|CTR| WORKER-INFO UPDATE: SRC<"+ newWi.getId() + "> Qi<" +oldWi.getQueueSize()+"->" +newWi.getQueueSize() + "> Wi <" +oldWi.getW_i()+"->"  + newWi.getW_i() + ">");

                oldWi.setW_i(newWi.getW_i());
                oldWi.setQueueSize(newWi.getQueueSize());
                oldWi.setNodeProcessingPower(newWi.getNodeProcessingPower());
                oldWi.setAverageTaskSize(newWi.getAverageTaskSize());

                nodeUpdateEventList.addLast(oldWi.getId());
                return;
            }
        }
        Log.info("|CTR| WORKER-INFO ADD: SRC<"+ newWi.getId() + "> Qi<" +"->" +newWi.getQueueSize() + "> Wi <" + newWi.getW_i() + ">");
        // Means no node with given Id has sent information to the Controller yet.
        // Only happens with nodes that joined later. All nodes known from beginning are init with a 0 (?).
        workerInfo.add(newWi);
    }

    /**
     * This method will generate an entry for each of the nodes in the network excluding the controller node.
     * Note: In the future perhaps have two different modes. One where the Controller participates in the network,
     * another where the controller is just managing the network (curretn implementation is this where the controller is
     * just managing the network).
     * @param node
     * @param protocolID
     */
    void initializeWorkerInfo(Node node, int protocolID) {
        double default_task_size = Configuration.getDouble( "protocol.clt.I", 200e6);
        double default_CPU_FREQ = Configuration.getDouble( "protocol.wrk.FREQ", 1e7);
        int default_CPU_NO_CORES = Configuration.getInt( "protocol.wrk.NO_CORES", 4);
        int linkableID = FastConfig.getLinkable(protocolID);
        Linkable linkable = (Linkable) node.getProtocol(linkableID);
        for(int i = 1; i < linkable.degree(); i++){
            Node target = linkable.getNeighbor(i);
            if (!target.isUp()) return; // This happens task progress is lost.
            Worker wi = ((Worker) target.getProtocol(Worker.getPid()));
            workerInfo.add(
                    new WorkerInfo(wi.getId(), 0, 0, default_task_size, Math.floor(default_CPU_NO_CORES*default_CPU_FREQ))
            );
        }
    }
    //=== Reward Function
    private double calculatReward(Linkable linkable, Node n, int targetNode, int noTasks) {
        //== Setup the variables as explained.
        WorkerInfo initialInfo = getWorkerInfo(selectedNode);
        WorkerInfo targetInfo = getWorkerInfo(targetNode);
        assert initialInfo != null;
        assert targetInfo != null;

        // when number of tasks to offload is bigger than number of tasks nothing happens

        Node t = linkable.getNeighbor(targetNode);
        double w_o = (initialInfo.getW_i() < noTasks) ? 0 : noTasks;
        double w_l = initialInfo.getW_i() - w_o;
        double Q_l = initialInfo.getQueueSize(); // This will never be 0 with my current implementation.
        double Q_o = targetInfo.getQueueSize(); // initial Queue size plus the tasks that stayed.
        double miu_l = initialInfo.getW();
        double miu_o = targetInfo.getW();
        double Q_expected_l = initialInfo.getQueueSize() - w_o;
        double Q_expected_o = targetInfo.getQueueSize() + w_o; // initial Queue size plus the tasks that stayed.

        //== Computing the reward
        Log.dbg("Acquiring Reward");

        Client clt = this.getClient();

        //== Immediate Utility
        double U = UTILITY_REWARD * Math.log(1 + w_l + w_o );

        //== Immediate Delay
        //= Delay of Sitting in Queue
        double t_w = notZero(w_l) * (Q_l/miu_l) + notZero(w_o) * ((Q_l/miu_l) + (Q_o/miu_o)) ;


        SDNNodeProperties propsNode =  (SDNNodeProperties) n.getProtocol(SDNNodeProperties.getPid());
        SDNNodeProperties propsTarget =  (SDNNodeProperties) t.getProtocol(SDNNodeProperties.getPid());
        double d_i_j = Math.sqrt(Math.pow(propsNode.getY() - propsTarget.getY(), 2) + Math.pow(propsNode.getX() - propsTarget.getX(), 2));
        double r_i_j = propsNode.getBANDWIDTH() * Math.log(1 +
                ( propsTarget.getPATH_LOSS_CONSTANT() * Math.pow(d_i_j, propsNode.getPATH_LOSS_EXPONENT()) * propsNode.getTRANSMISSION_POWER() ) / ( propsNode.getBANDWIDTH() * NORMALIZED_THERMAL_NOISE_POWER) );
        //= Communication Delay
        double t_c = (d_i_j == 0) ? 0 : 2 * w_o * clt.BYTE_SIZE / r_i_j ; // This can be a NaN
        // When the instruction to offload to itself is given then the cost of communication is 0. (aka d_i_j == 0)

        //= Task Execution Delay
        double t_e = clt.NO_INSTR * clt.CPI * (w_l / ((Worker) n.getProtocol(Worker.getPid())).CPU_FREQ  + w_o / ((Worker) t.getProtocol(Worker.getPid())).CPU_FREQ);

        double D = (w_l == 0 && w_o == 0) ?  0 :  DELAY_WEIGHT * (t_w + t_c + t_e)/(w_l + w_o);
        // If no transaction is being done and there is nothing to process locally then there is no delay. And can't divide by 0.

        //== Overload Probability
        // Note one simplification I take is look at each node as it's own M/M/1.
        Worker workerNode = (Worker) n.getProtocol(Worker.getPid());
        Worker workerTarget = (Worker) t.getProtocol(Worker.getPid());

        double Q_prime_l = Math.min(Math.max(0, Q_expected_l - workerNode.getProcessingPower()) + w_l, workerNode.Q_MAX); // This should technically be available in the WorkerInfo btw...
        double Q_prime_o = Math.min(Math.max(0, Q_expected_o - workerTarget.getProcessingPower()) + w_o, workerTarget.Q_MAX);

        double pOverload_l = Math.max(0, EXPECTED_TASK_ARRIVAL_RATE - Q_prime_l);
        double pOverload_o = Math.max(0, EXPECTED_TASK_ARRIVAL_RATE - Q_prime_o);
// REMOVED FOR TESTING PURPOSES -> (w_l == 0 && w_o == 0) ? 0 :
        double O =  OVERLOAD_WEIGHT * (w_l * pOverload_l + w_o * pOverload_o)/(w_l + w_o); // Same logic applied in calculating D.

        return U - (D + O);
    }

    private double notZero(double n){
        return (n == 0)? 0 : 1;
    }

    //=== Logging and Debugging
    private void printParams(){
        //if(active)
        Log.dbg("Controller Params: r_u<" + this.UTILITY_REWARD + "> X_d<" + this.DELAY_WEIGHT+ "> X_o<"+ this.OVERLOAD_WEIGHT+">" );
    }

    //======================================================================================================
    // Interface Methods

    //======================================================================================================
    public List<WorkerInfo> getWorkerInfo() {
        return workerInfo;
    }

    public boolean isActive() {
        return active;
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

    public List<Integer> getQ(){
        int start = 1; // By definition can't have less than 3 nodes. For convenience
        return this.workerInfo.stream().map(WorkerInfo::getQueueSize).toList();
    }

    public EnvState getState(){
        Log.dbg("Acquiring state");
        // stop = true; Set the await action to block on next iter.
        double w = getWorkerInfo(this.selectedNode).getW();
        return new EnvState(this.selectedNode, this.getQ(), w);
    }
    public Client getClient(){
        return (Client) Network.get(0).getProtocol(Client.getPid());
    }

    public boolean isUp() {
        return up;
    }

    public boolean isStable() {
        return stop;
    }

    public DebugInfo getDebugInfo() {
        return new DebugInfo(this.selectedNode);
    }
}
