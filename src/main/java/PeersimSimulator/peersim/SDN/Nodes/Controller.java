package PeersimSimulator.peersim.SDN.Nodes;

import PeersimSimulator.peersim.SDN.Links.SDNNodeProperties;
import PeersimSimulator.peersim.SDN.Util.Log;
import PeersimSimulator.peersim.SDN.Nodes.Events.OffloadInstructions;
import PeersimSimulator.peersim.SDN.Nodes.Events.WorkerInfo;
import PeersimSimulator.peersim.SDN.Records.Action;
import PeersimSimulator.peersim.SDN.Records.EnvState;
import PeersimSimulator.peersim.cdsim.CDProtocol;
import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.config.FastConfig;
import PeersimSimulator.peersim.core.CommonState;
import PeersimSimulator.peersim.core.Linkable;
import PeersimSimulator.peersim.core.Network;
import PeersimSimulator.peersim.core.Node;
import PeersimSimulator.peersim.edsim.EDProtocol;
import PeersimSimulator.peersim.transport.Transport;

import java.util.ArrayList;
import java.util.List;


public class Controller implements CDProtocol, EDProtocol {
    private static final String PAR_NAME = "name";

    private final int UTILITY_REWARD;
    private final static String PAR_UTILITY_REWARD = "r_u";

    private final int DELAY_WEIGHT;
    private final static String PAR_DELAY_WEIGHT = "X_d";


    private final int OVERLOAD_WEIGHT;
    private final static String PAR_OVERLOAD_WEIGHT = "X_o";

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

    int selectedNode;
    List<WorkerInfo> workerInfo;
    private int id;

    private volatile boolean stop;
    public Controller(String prefix) {
        pid = Configuration.getPid(prefix+ "."+PAR_NAME);
        workerInfo = new ArrayList<>();
        active = false;
        stop = true;
        // Read Constants
        DELAY_WEIGHT = Configuration.getInt( prefix + "." + PAR_DELAY_WEIGHT, 1) ;
        OVERLOAD_WEIGHT = Configuration.getInt( prefix + "." + PAR_OVERLOAD_WEIGHT, 150);
        UTILITY_REWARD = Configuration.getInt( prefix + "." + PAR_UTILITY_REWARD, 1);
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
        stop = true;
        awaitAction();
    }



    public double sendAction(Action a) {
        if(!active || a == null || a.noTasks() < 0 || a.nodeId() < 0) return -1;

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


            selectedNode = (selectedNode + 1) % linkable.degree();
            Node selectedWorker = linkable.getNeighbor(selectedNode);
            Worker protocol = (Worker) selectedWorker.getProtocol(Worker.getPid());

            if(!selectedWorker.isUp() || workerInfo.isEmpty()) return -1;

            int targetNode = a.nodeId();
            int noTasks = a.noTasks();

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

            // Compute Reward:


            return reward;

        }
        return -1;

    }

    private double calculatReward(Linkable linkable, Node selectedWorker, int targetNode, int noTasks) {
        WorkerInfo initialInfo = getWorkerInfo(selectedNode);
        WorkerInfo targetInfo = getWorkerInfo(targetNode);
        assert initialInfo != null;
        assert targetInfo != null;

        int w_o = (initialInfo.getW_i() < noTasks) ? 0 : noTasks; // when number of tasks to offload is bigger than number of tasks nothing happens.
        double reward = evaluateState(
                selectedWorker,
                linkable.getNeighbor(targetNode),
                initialInfo.getW_i() - w_o,
                w_o,
                initialInfo.getQueueSize(),
                targetInfo.getQueueSize(),// initial Queue size plus the tasks that stayed.
                initialInfo.getW(),
                targetInfo.getW(),
                initialInfo.getQueueSize() - w_o,
                targetInfo.getQueueSize() + w_o// initial Queue size plus the tasks that stayed.

        );
        return reward;
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
    // Interface Methods
    //======================================================================================================

    public List<WorkerInfo> getWorkerInfo() {
        return workerInfo;
    }

    public void setWorkerInfo(List<WorkerInfo> workerInfo) {
        this.workerInfo = workerInfo;
    }

    public EnvState getState(){
        Log.dbg("Acquiring state");
        // stop = true; Set the await action to block on next iter.
        double w = this.workerInfo.get(selectedNode).getW();
        return new EnvState(this.selectedNode, this.getQ(), w);
    }
    public double evaluateState(Node n, Node t, double w_l, double w_o, double Q_l, double Q_o, double miu_l, double miu_o, double Q_expected_l, double Q_expected_o) {
        Log.dbg("Acquire Reward");

        Client clt = this.getClient();

        // Immediate Utility
        double U = UTILITY_REWARD * Math.log(1 + w_l + w_o );

        // Immediate Delay
        double t_w = notZero(w_l) * (Q_l/miu_l) + notZero(w_o) * ((Q_l/miu_l) + (Q_o/miu_o)) ;


        SDNNodeProperties propsNode =  (SDNNodeProperties) n.getProtocol(SDNNodeProperties.getPid());
        SDNNodeProperties propsTarget =  (SDNNodeProperties) t.getProtocol(SDNNodeProperties.getPid());
        double d_i_j = Math.sqrt(Math.pow(propsNode.getY() - propsTarget.getY(), 2) + Math.pow(propsNode.getX() - propsTarget.getX(), 2));
        double r_i_j = propsNode.getBANDWIDTH() * Math.log(1 +
                ( propsTarget.getPATH_LOSS_CONSTANT() * Math.pow(d_i_j, propsNode.getPATH_LOSS_EXPONENT()) * propsNode.getTRANSMISSION_POWER() ) / ( propsNode.getBANDWIDTH() * NORMALIZED_THERMAL_NOISE_POWER) );


        double t_c = 2 * w_o * clt.BYTE_SIZE / r_i_j; // TODO Works only when all tasks have the same size,
                                                         // TODO change to have average byte size, send info in the WorkerInfo.


        double t_e = clt.NO_INSTR * clt.CPI * (w_l / ((Worker) n.getProtocol(Worker.getPid())).CPU_FREQ  + w_o / ((Worker) t.getProtocol(Worker.getPid())).CPU_FREQ);

        double D = (w_l == 0 && w_o == 0) ?  0 :  DELAY_WEIGHT * (t_w + t_c + t_e)/(w_l + w_o);
        // If no transaction is being done and there is nothing to process locally then there is no delay. And can't divide by 0.

        // Overload Probability - Note one simplification I take is look at each node as it's own M/M/1.
        // I consider that overloaded means the tasks accumulate above a certain point. TODO think if I should just drop them?

        Worker workerNode = (Worker) n.getProtocol(Worker.getPid());
        Worker workerTarget = (Worker) t.getProtocol(Worker.getPid());

        double Q_prime_l = Math.min(Math.max(0, Q_expected_l - workerNode.getProcessingPower()) + w_l, workerNode.Q_MAX); // This should technically be available in the WorkerInfo btw...
        double Q_prime_o = Math.min(Math.max(0, Q_expected_o - workerTarget.getProcessingPower()) + w_o, workerTarget.Q_MAX);

        double pOverload_l = Math.max(0, Client.getTaskArrivalRate() - Q_prime_l);
        double pOverload_o = Math.max(0, Client.getTaskArrivalRate() - Q_prime_o);

        double O = (w_l == 0 && w_o == 0) ? 0 : OVERLOAD_WEIGHT * (w_l * pOverload_l + w_o * pOverload_o)/(w_l + w_o); // Same logic applied in calculating D.

        return U - (D + O);
    }

    /**
     * Spin waits for the next action.
     */
    private void awaitAction() {
        Log.dbg("Start Waiting");
        while (stop) Thread.onSpinWait();
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

    public boolean isStable() {
        return stop;
    }

    public double notZero(double n){
        return (n == 0)? 0 : 1;
    }

    public List<Integer> getQ(){
        return this.workerInfo.stream().map(WorkerInfo::getQueueSize).toList();
    }

    public Client getClient(){
        return (Client) Network.get(0).getProtocol(Client.getPid());
    }

    //======================================================================================================
    // Private Methods
    //======================================================================================================

    private void updateNode(WorkerInfo newWi){
        for (WorkerInfo oldWi : workerInfo) {
            if(oldWi.getId() == newWi.getId()){
                Log.info("|CTR| WORKER-INFO UPDATE: SRC<"+ newWi.getId() + "> Qi<" +oldWi.getQueueSize()+"->" +newWi.getQueueSize() + "> Wi <" +oldWi.getW_i()+"->"  + newWi.getW_i() + ">");

                oldWi.setW_i(newWi.getW_i());
                oldWi.setQueueSize(newWi.getQueueSize());
                oldWi.setNodeProcessingPower(newWi.getNodeProcessingPower());
                oldWi.setAverageTaskSize(newWi.getAverageTaskSize());
                return;
            }
        }
        Log.info("|CTR| WORKER-INFO ADD: SRC<"+ newWi.getId() + "> Qi<" +"->" +newWi.getQueueSize() + "> Wi <" + newWi.getW_i() + ">");
        // Means no node with given Id has sent information to the Controller yet.
        // Only happens with nodes that joined later. All nodes known from beginning are init with a 0 (?).
        workerInfo.add(newWi);
    }
    void initializeWorkerInfo(Node node, int protocolID) {
        double default_task_size = Configuration.getDouble( "protocol.clt.I", 200e6);
        double default_CPU_FREQ = Configuration.getDouble( "protocol.wrk.FREQ", 1e7);
        int default_CPU_NO_CORES = Configuration.getInt( "protocol.wrk.NO_CORES", 4);
        int linkableID = FastConfig.getLinkable(protocolID);
        Linkable linkable = (Linkable) node.getProtocol(linkableID);
        for(int i = 0; i < linkable.degree(); i++){
            Node target = linkable.getNeighbor(i);
            if (!target.isUp()) return; // This happens task progress is lost.
            Worker wi = ((Worker) target.getProtocol(Worker.getPid()));
            workerInfo.add(
                    new WorkerInfo(wi.getId(), 0, 0, default_task_size, Math.floor(default_CPU_NO_CORES*default_CPU_FREQ))
            );
        }
    }

    private void printParams(){
        //if(active)
            Log.dbg("Controller Params: r_u<" + this.UTILITY_REWARD + "> X_d<" + this.DELAY_WEIGHT+ "> X_o<"+ this.OVERLOAD_WEIGHT+">" );
    }


}
