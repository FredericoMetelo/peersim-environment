package PeersimSimulator.peersim.env.SimulationManagers;

import PeersimSimulator.peersim.cdsim.CDProtocol;
import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.config.FastConfig;
import PeersimSimulator.peersim.core.CommonState;
import PeersimSimulator.peersim.core.Linkable;
import PeersimSimulator.peersim.core.Network;
import PeersimSimulator.peersim.core.Node;
import PeersimSimulator.peersim.env.Links.SDNNodeProperties;
import PeersimSimulator.peersim.env.Nodes.Clients.Client;
import PeersimSimulator.peersim.env.Nodes.Cloud.Cloud;
import PeersimSimulator.peersim.env.Nodes.Controllers.Controller;
import PeersimSimulator.peersim.env.Nodes.Events.CloudInfo;
import PeersimSimulator.peersim.env.Nodes.Workers.Worker;
import PeersimSimulator.peersim.env.Records.*;
import PeersimSimulator.peersim.env.Records.Actions.Action;
import PeersimSimulator.peersim.env.Records.SimulationData.SimulationData;
import PeersimSimulator.peersim.env.Util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public abstract class AbstractTimeStepManager implements CDProtocol  {
    /**
     * The protocol to operate on.
     */
    protected static final String PAR_NAME = "name";
    protected final static String PAR_CYCLE = "CYCLE";
    protected final static String PAR_CTR_ID = "CONTROLLERS";
    protected static final String PAR_HAS_CLOUD = "CLOUD_EXISTS";
    /**
     * Protocol identifier,
     */
    protected static int pid;
    public final int CYCLE_SIZE;
    public final double EXPECTED_TASK_ARRIVAL_RATE;
    /**
     * The name of this observer in the configuration file.
     */
    protected final String name;
    protected List<Integer> controllerIDs;
    protected int hasCloud;
    /**
     * up and stop are the variables responsible for the flow management of the application.
     * up - represents the state of the application, if the application is running up will be true and the controller will accept requests from the agent.tap:
     * stop - is the variable that says if the application is executing and can't retrieve data. Every 100 ticks the simulation checks if the event queue is not empty.
     * And selects the next node to offload from.
     */
    protected volatile boolean stop;
    protected volatile boolean up;
    protected boolean active = false;

    public AbstractTimeStepManager(String prefix) {
        pid = Configuration.getPid(prefix + "." + PAR_NAME);

        CYCLE_SIZE = Configuration.getInt(PAR_CYCLE, 1);
        hasCloud = Configuration.getInt(PAR_HAS_CLOUD, 0);


        String _ctrIds = Configuration.getString(PAR_CTR_ID, "0");
        controllerIDs = Arrays.stream(_ctrIds.split(";")).distinct().map(Integer::parseInt).toList();
        mngDbgLog(this.controllersToString());
        EXPECTED_TASK_ARRIVAL_RATE = Configuration.getDouble(prefix + "." + Client.PAR_TASKARRIVALRATE, Client.DEFAULT_TASKARRIVALRATE);
        this.name = prefix;
        stop = false;
        up = false;
    }

    @Override
    public Object clone() {
        AbstractTimeStepManager svh = null;
        try {
            svh = (AbstractTimeStepManager) super.clone();
        } catch (CloneNotSupportedException e) {
        } // never happens
        return svh;
    }
    public abstract List<SimulationData> sendAction(List<Action> actionList);

    public State getState() {
        List<PartialState> partialStates = getPartialStates();
        GlobalState globalState = getGlobalState();
        return new State(partialStates, globalState);
    }


    public List<PartialState> getPartialStates() {
        List<PartialState> partialStateList = new ArrayList<>(controllerIDs.size());
        for (int id : controllerIDs) {
            Controller c = (Controller) Network.get(id).getProtocol(Controller.getPid());
            partialStateList.add(c.getState());
        }
        return partialStateList;
    }

    public GlobalState getGlobalState() {
        List<Integer> nodeIds = new ArrayList<>(Network.size());
        List<Integer> queues = new ArrayList<>(Network.size());
        List<Double> processingPowers = new ArrayList<>(Network.size());
        List<Integer> layers = new ArrayList<>(Network.size());
        List<Integer> noCores = new ArrayList<>(Network.size());
        List<Coordinates> positions = new ArrayList<>(Network.size());
        List<Double> bandwidths = new ArrayList<>(Network.size());
        List<Double> transmissionPowers = new ArrayList<>(Network.size());
        List<Integer> overloadedTimes = new ArrayList<>(Network.size());
        List<Integer> droppedByExpirationOnNode = new ArrayList<>(Network.size());
        List<Integer> droppedOnArrivalOnNode = new ArrayList<>(Network.size());
        List<Double> taskCompletionTimes = new ArrayList<>(Network.size());
        List<Integer> finishedTasks = new ArrayList<>(Network.size());
        List<Integer> droppedTasks = new ArrayList<>(Network.size());
        List<Integer> totalTasks = new ArrayList<>(Network.size());
        List<Integer> isWorking = new ArrayList<>(Network.size());
        List<Double> energyConsumed = new ArrayList<>(Network.size());

        CloudInfo cinfo = null;
        if(hasCloud == 1) {
            Node cloud = Network.get(Network.size() - 1);
            Cloud cloudWorker = (Cloud) cloud.getProtocol(Cloud.getPid());
            cinfo = cloudWorker.cloudInfo();
        }

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
                energyConsumed.add(worker.getEnergyConsumed());
                overloadedTimes.add(worker.getTimesOverloaded());
                droppedByExpirationOnNode.add(worker.getNoExpiredTasks());
                droppedOnArrivalOnNode.add(worker.failedOnArrivalToNode());

                SDNNodeProperties props = worker.getProps();
                positions.add(new Coordinates(props.getX(), props.getY()));
                bandwidths.add(props.getBANDWIDTH());
                transmissionPowers.add(props.getTRANSMISSION_POWER());
                isWorking.add(worker.isWorking());

                Client client = (Client) n.getProtocol(Client.getPid());
                if (client == null || !client.isActive()) {
                    continue;
                }
                taskCompletionTimes.add(client.getAverageTaskCompletionTime());
                droppedTasks.add(client.getDroppedTasks());
                finishedTasks.add(client.getTasksCompleted());
                totalTasks.add(client.getTotalTasks());
            }
        }
        return new GlobalState(nodeIds, queues, processingPowers, noCores, layers, positions, bandwidths,
                transmissionPowers, taskCompletionTimes, droppedTasks, finishedTasks, totalTasks, isWorking, energyConsumed,overloadedTimes, droppedOnArrivalOnNode, droppedByExpirationOnNode, cinfo);
    }



    public boolean isActive() {
        return active;
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


    public double notZero(double n) {
        return (n == 0) ? 0 : 1;
    }


    public String controllersToString() {
        StringBuilder s = new StringBuilder("[ ");
        for (int id : controllerIDs
        ) {
            s.append(id).append(" ");
        }
        s.append("]");
        return s.toString();
    }


    public void mngInfoLog(String event, String info) {
        Log.logInfo("MNG", -1, event, info);

    }


    public void mngDbgLog(String msg) {
        Log.logDbg("MNG", -1, "DEBUG", msg);
    }


    public void mngErrLog(String msg) {
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
        List<List<Integer>> knowsControllerMatrix = new ArrayList<>(Network.size());
        for (int i = 0; i < Network.size(); i++) {
            Node n = Network.get(i);
            Linkable neighbourNodes = (Linkable) n.getProtocol(FastConfig.getLinkable(Worker.getPid()));
            int[] neighbours = AbstractTimeStepManager.getNeighbours(n);
            if (n.isUp()) {
                Linkable linkable = (Linkable) n.getProtocol(FastConfig.getLinkable(Worker.getPid()));
                int size = linkable.degree();
                if (size < min) min = size;
                if (size > max) max = size;
                average += size;
                average /= 2;
            }
            neighbourMatrix.add(Arrays.stream(neighbours).boxed().toList());

            knowsControllerMatrix.add(AbstractTimeStepManager.getNeighborsWithControllerIndices(neighbours));
        }
        return new NetworkData(min, max, average, neighbourMatrix, knowsControllerMatrix);
    }

    /**
     * This method returns the indices in the node's neighbourhood of the node's neighbours that are known to host a controller as well.
     * @param neighbours
     * @return
     */
    public static List<Integer> getNeighborsWithControllerIndices(int[] neighbours) {
        List<Integer> indices = new LinkedList<>();
        for (int i = 0; i < neighbours.length; i++) {
            int id = neighbours[i];
            Controller c = (Controller) Network.get(id).getProtocol(Controller.getPid());
            if (c.isActive()) {
                indices.add(i);
            }
        }
        return indices;
    }

    public static int[] getNeighbours(Node n) {
        int linkableID = FastConfig.getLinkable(Worker.getPid());
        Linkable l = (Linkable) n.getProtocol(linkableID);
        String neighboursString = l.toString();
        if (neighboursString.equals("DEAD!")) return new int[0];
        int[] neighbours = Arrays.stream(neighboursString.substring(neighboursString.indexOf("[") + 1, neighboursString.indexOf(" ]")).split(" ")).mapToInt(Integer::parseInt).toArray(); // This is a bit of a hack.
        return neighbours;
    }

    static int getPid() {
        return AbstractTimeStepManager.pid;
    }

    public abstract List<SimulationData> forward(List<Action> a);
}
