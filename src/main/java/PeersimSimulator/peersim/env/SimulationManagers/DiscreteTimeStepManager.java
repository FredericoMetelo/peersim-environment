package PeersimSimulator.peersim.env.SimulationManagers;

import PeersimSimulator.peersim.env.Links.SDNNodeProperties;
import PeersimSimulator.peersim.env.Nodes.Clients.Client;
import PeersimSimulator.peersim.env.Nodes.Controllers.Controller;
import PeersimSimulator.peersim.env.Nodes.Workers.Worker;
import PeersimSimulator.peersim.env.Records.*;
import PeersimSimulator.peersim.env.Records.Actions.Action;
import PeersimSimulator.peersim.env.Records.SimulationData.SimulationData;
import PeersimSimulator.peersim.env.Util.Log;
import PeersimSimulator.peersim.cdsim.CDProtocol;
import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.config.FastConfig;
import PeersimSimulator.peersim.core.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DiscreteTimeStepManager implements CDProtocol {

    /////////////////////////////////////////////////////////////////////////
    // Constants
    /////////////////////////////////////////////////////////////////////////
    /**
     * The protocol to operate on.
     */
    private static final String PAR_NAME = "name";

    private final static String PAR_CYCLE = "CYCLE";
    private final static String PAR_CTR_ID = "CONTROLLERS";

    private static final String PAR_HAS_CLOUD = "CLOUD_EXISTS";


    public final int CYCLE_SIZE;

    public final double EXPECTED_TASK_ARRIVAL_RATE;

    /**
     * The Normalized Thermal Noise power is measured in dBm/Hz. From "https://noisewave.com/faq.pdf"
     * <p>
     * > Noise Figure is defined as the ratio of the signal-to-noise power at the input to the signal-to-noise power
     * > at the output of a device, in other words the degradation of the signal-to-noise ratio as the signal passes
     * > through the device. Since the input noise level is usually thermal noise from the source the convention is
     * > to adopt a reference temperature of 290º K. The noise figure becomes the ratio of the total noise
     * > power output to that portion of the noise power output due to noise at input when the source is 290º
     * > K. ...
     * The thermal noise power  at 290º K is following the same sources is constant and equal to -174 dBm/Hz
     */
    public final static int NORMALIZED_THERMAL_NOISE_POWER = 174;

    private List<Integer> controllerIDs;

    /////////////////////////////////////////////////////////////////////////
    // Fields
    /////////////////////////////////////////////////////////////////////////
    /**
     * The name of this observer in the configuration file.
     */
    private final String name;

    /** Protocol identifier,*/
    private static int pid;

    private int hasCloud;


    /**
     * up and stop are the variables responsible for the flow management of the application.
     * up - represents the state of the application, if the application is running up will be true and the controller will accept requests from the agent.tap:
     * stop - is the variable that says if the application is executing and can't retrieve data. Every 100 ticks the simulation checks if the event queue is not empty.
     * And selects the next node to offload from.
     */
    private volatile boolean stop, up;
    private boolean active = false;

    /////////////////////////////////////////////////////////////////////////
    // Constructor
    /////////////////////////////////////////////////////////////////////////
    public DiscreteTimeStepManager(String prefix) {
        this.name = prefix;
        pid = Configuration.getPid(prefix + "." + PAR_NAME);
        up = false;
        stop = false;

        CYCLE_SIZE = Configuration.getInt(PAR_CYCLE, 100);
        hasCloud = Configuration.getInt(PAR_HAS_CLOUD, 0);
        EXPECTED_TASK_ARRIVAL_RATE = Configuration.getDouble(prefix + "." + Client.PAR_TASKARRIVALRATE, Client.DEFAULT_TASKARRIVALRATE);



        String _ctrIds = Configuration.getString(PAR_CTR_ID, "0");
        controllerIDs = Arrays.stream(_ctrIds.split(";")).distinct().map(Integer::parseInt).toList();
        mngDbgLog(this.controllersToString());
    }

    /////////////////////////////////////////////////////////////////////////
    // Methods
    /////////////////////////////////////////////////////////////////////////
    @Override
    public void nextCycle(Node node, int protocolID) {
        if(!active) {
            return;
        }
        up = true;
        if(CommonState.getTime() % CYCLE_SIZE == 0) {
            stop = true;
            awaitAction();
        }
    }

    @Override
    public Object clone() {
        DiscreteTimeStepManager svh = null;
        try {
            svh = (DiscreteTimeStepManager) super.clone();
        } catch (CloneNotSupportedException e) {
        } // never happens
        return svh;
    }

    public List<SimulationData> sendAction(List<Action> actionList){
        mngDbgLog(actionList.toString());
        List<SimulationData> results = new ArrayList<>(actionList.size());
        if(actionList.size() != controllerIDs.size()) {
            mngErrLog("Illegal number of Actions in joint-Action. Not progressing this iteration.");
            return null;
        }
        for (Action a : actionList) {
            // Never forget that the wildcard must extend action.
            // Some problems may arise if jackson can't distinguih the type of the action.
            // So I may need to configure an ObjectMapper to handle this.
            //Action a = wa.getAction();
            int controllerId = a.controllerId();
            if(!controllerIDs.contains(controllerId)){
                mngErrLog("An action was sent for id " + controllerId + "this id does not correspond to any controller. Ignoring action.");
            }



            Controller c = (Controller) Network.get(controllerId).getProtocol(Controller.getPid());
            if(!c.isActive()) throw new RuntimeException("Inactive BasicController id=" + controllerId);
            SimulationData result = c.sendAction(a);
            results.add(result);
        }
        try{
            mngDbgLog(new ObjectMapper().writeValueAsString(results));
        } catch (Exception e) {
            e.printStackTrace();
        }
        stop = false;
        return results;
    }

    public State getState() {
        List<PartialState> partialStates = getPartialStates();
        GlobalState globalState = getGlobalState();
        return new State(partialStates, globalState);
    }

    public List<PartialState> getPartialStates(){
        List<PartialState> partialStateList = new ArrayList<>(controllerIDs.size());
        for(int id : controllerIDs) {
            Controller c = (Controller) Network.get(id).getProtocol(Controller.getPid());
            partialStateList.add(c.getState());
        }
        return partialStateList;
    }

    private GlobalState getGlobalState() {
        List<Integer> nodeIds = new ArrayList<>(Network.size());
        List<Integer> queues = new ArrayList<>(Network.size());
        List<Double>  processingPowers = new ArrayList<>(Network.size());
        List<Integer> layers = new ArrayList<>(Network.size());
        List<Integer> noCores  = new ArrayList<>(Network.size());
        List<Coordinates> positions = new ArrayList<>(Network.size());
        List<Double> bandwidths = new ArrayList<>(Network.size());
        List<Double> transmissionPowers = new ArrayList<>(Network.size());
        List<Double> taskCompletionTimes = new ArrayList<>(Network.size());
        List<Integer> finishedTasks = new ArrayList<>(Network.size());
        List<Integer> droppedTasks = new ArrayList<>(Network.size());
        List<Integer> totalTasks = new ArrayList<>(Network.size());

        for (int i = 0; i < Network.size() - hasCloud; i++) {
            Node n = Network.get(i);
            if (n.isUp()) {
                Linkable linkable = (Linkable) n.getProtocol(FastConfig.getLinkable(Worker.getPid()));
                Worker worker = (Worker) n.getProtocol(Worker.getPid());
                nodeIds.add(worker.getId());
                queues.add(worker.getTotalNumberOfTasksInNode());
                processingPowers.add(worker.getProcessingPower());
                layers.add(worker.getLayer());
                noCores.add(worker.getCpuNoCores());
                SDNNodeProperties props = worker.getProps();
                positions.add(new Coordinates(props.getX(), props.getY()));
                bandwidths.add(props.getBANDWIDTH());
                transmissionPowers.add(props.getTRANSMISSION_POWER());

                Client client = (Client) n.getProtocol(Client.getPid());
                if(client == null || !client.isActive())
                    continue;
                taskCompletionTimes.add(client.getAverageTaskCompletionTime());
                droppedTasks.add(client.getDroppedTasks());
                finishedTasks.add(client.getTasksCompleted());
                totalTasks.add(client.getTotalTasks());
            }
        }
        return new GlobalState(nodeIds, queues, processingPowers, noCores, layers, positions, bandwidths,
                                transmissionPowers, taskCompletionTimes, droppedTasks, finishedTasks, totalTasks);
    }


    private void awaitAction() {
        mngDbgLog("Start Waiting Time<" + CommonState.getTime() + ">");
        while (stop) Thread.onSpinWait();
    }


    public boolean isActive() {
        return active;
    }
    public static int getPid() {
        return pid;
    }

    public boolean isUp() {
        mngDbgLog("isUp: " + up);
        return up;
    }
    public boolean isStable() {
        mngDbgLog("isStable: " + stop);
        return stop;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    private double notZero(double n) {
        return (n == 0) ? 0 : 1;
    }
    private String controllersToString(){
        StringBuilder s = new StringBuilder("[ ");
        for (int id: controllerIDs
             ) {
            s.append(id).append(" ");
        }
        s.append("]");
        return s.toString();
    }
    public void mngInfoLog(String event, String info){
        Log.logInfo("MNG", -1, event, info);

    }

    public void mngDbgLog(String msg){
        Log.logDbg("MNG", -1, "DEBUG", msg);
    }

    public void mngErrLog(String msg){
        Log.logErr("MNG", -1, "ERROR", msg);
    }

    public DebugInfo getDebugInfo() {
        // TODO
        return null;
    }

    public NetworkData getNeighbourData() {
        int min = Network.size();
        int max = 0;
        int average = 0;
        List<List<Integer>> neighbourMatrix = new ArrayList<>(Network.size());
        for (int i = 0; i < Network.size(); i++) {
            Node n = Network.get(i);
            int[] neighbours = getNeighbours(n);
            if (n.isUp()) {
                Linkable linkable = (Linkable) n.getProtocol(FastConfig.getLinkable(Worker.getPid()));
                int size = linkable.degree();
                if (size < min) min = size;
                if (size > max) max = size;
                average += size;
                average /= 2;
            }
            neighbourMatrix.add(Arrays.stream(neighbours).boxed().toList());
        }
        return new NetworkData(min, max, average, neighbourMatrix);
    }

    private static int[] getNeighbours(Node n) {
        int linkableID = FastConfig.getLinkable(Worker.getPid());
        Linkable l = (Linkable) n.getProtocol(linkableID);
        String neighboursString = l.toString();
        if (neighboursString.equals("DEAD!")) return new int[0];
        int[] neighbours = Arrays.stream(neighboursString.substring(neighboursString.indexOf("[") + 1, neighboursString.indexOf(" ]")).split(" ")).mapToInt(Integer::parseInt).toArray(); // This is a bit of a hack.
        return neighbours;
    }
    /*
    private double calculatReward(Linkable linkable, Node n, int targetNode, int controllerId) {
        //== Setup the variables as explained.
        WorkerInfo initialInfo = getWorkerInfo(selectedNode);
        WorkerInfo targetInfo = getWorkerInfo(targetNode);

        // Behaviour for when the node is out of scope? Return error? Have distribution over the known nodes?
        if (targetInfo == null || initialInfo == null) return WRONG_MOVE_PUNISHMENT;

        // when number of tasks to offload is bigger than number of tasks nothing happens
        Node t = linkable.getNeighbor(targetNode);
        double w_o = (initialInfo.getW_i() < controllerId) ? 0 : controllerId;
        double w_l = initialInfo.getW_i() - w_o;
        double Q_l = initialInfo.getTotalTasks(); // This will never be 0 with my current implementation.
        double Q_o = targetInfo.getTotalTasks(); // initial Queue size plus the tasks that stayed.
        double miu_l = initialInfo.getW();
        double miu_o = targetInfo.getW();
        double Q_expected_l = initialInfo.getTotalTasks() - w_o;
        double Q_expected_o = targetInfo.getTotalTasks() + w_o; // initial Queue size plus the tasks that stayed.

        //== Computing the reward
        ctrDbgLog("Acquiring Reward");


        //== Immediate Utility
        double U = UTILITY_REWARD * Math.log(1 + w_l + w_o);

        //== Immediate Delay
        //= Delay of Sitting in Queue
        double t_w = notZero(w_l) * (Q_l / miu_l) + notZero(w_o) * ((Q_l / miu_l) + (Q_o / miu_o));


        SDNNodeProperties propsNode = (SDNNodeProperties) n.getProtocol(SDNNodeProperties.getPid());
        SDNNodeProperties propsTarget = (SDNNodeProperties) t.getProtocol(SDNNodeProperties.getPid());
        double d_i_j = Math.sqrt(Math.pow(propsNode.getY() - propsTarget.getY(), 2) + Math.pow(propsNode.getX() - propsTarget.getX(), 2));
        double r_i_j = propsNode.getBANDWIDTH() * Math.log(1 +
                (propsTarget.getPATH_LOSS_CONSTANT() * Math.pow(d_i_j, propsNode.getPATH_LOSS_EXPONENT()) * propsNode.getTRANSMISSION_POWER()) / (propsNode.getBANDWIDTH() * NORMALIZED_THERMAL_NOISE_POWER));
        //= Communication Delay
        double t_c = (d_i_j == 0) ? 0 : 2 * w_o * clt.getAverageByteSize() / r_i_j; // This can be a NaN
        // When the instruction to offload to itself is given then the cost of communication is 0. (aka d_i_j == 0)

        //= Task Execution Delay
        double t_e = clt.getAverageTaskSize() * (w_l / ((Worker) n.getProtocol(Worker.getPid())).CPU_FREQ + w_o / ((Worker) t.getProtocol(Worker.getPid())).CPU_FREQ);

        double D = (w_l == 0 && w_o == 0) ? 0 : DELAY_WEIGHT * (t_w + t_c + t_e) / (w_l + w_o);
        // If no transaction is being done and there is nothing to process locally then there is no delay. And can't divide by 0.

        //== Overload ProbabilityZ
        // Note one simplification I take is look at each node as it's own M/M/1.
        Worker workerNode = (Worker) n.getProtocol(Worker.getPid());
        Worker workerTarget = (Worker) t.getProtocol(Worker.getPid());

        double Q_prime_l = Math.min(Math.max(0, Q_expected_l - workerNode.getProcessingPower()) + w_l, workerNode.Q_MAX); // This should technically be available in the WorkerInfo btw...
        double Q_prime_o = Math.min(Math.max(0, Q_expected_o - workerTarget.getProcessingPower()) + w_o, workerTarget.Q_MAX);

        double pOverload_l = Math.max(0, EXPECTED_TASK_ARRIVAL_RATE - Q_prime_l);
        double pOverload_o = Math.max(0, EXPECTED_TASK_ARRIVAL_RATE - Q_prime_o);
// Update Reward functions?
        double O = (w_l == 0 && w_o == 0) ? 0 : OVERLOAD_WEIGHT * (w_l * pOverload_l + w_o * pOverload_o) / (w_l + w_o); // Same logic applied in calculating D.

        return U - (D + O);
    }
*/
}
