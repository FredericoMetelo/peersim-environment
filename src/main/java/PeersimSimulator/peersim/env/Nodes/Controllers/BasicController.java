package PeersimSimulator.peersim.env.Nodes.Controllers;

import PeersimSimulator.peersim.env.Links.SDNNodeProperties;
import PeersimSimulator.peersim.env.Nodes.Events.BasicOffloadInstructions;
import PeersimSimulator.peersim.env.Nodes.Workers.Worker;
import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.config.FastConfig;
import PeersimSimulator.peersim.core.Linkable;
import PeersimSimulator.peersim.core.Network;
import PeersimSimulator.peersim.core.Node;
import PeersimSimulator.peersim.env.Records.Actions.Action;
import PeersimSimulator.peersim.env.Records.Actions.BasicAction;
import PeersimSimulator.peersim.env.Records.SimulationData.BasicSimulationData;
import PeersimSimulator.peersim.env.Records.SimulationData.FailledActionSimulationData;
import PeersimSimulator.peersim.env.Records.SimulationData.SimulationData;


public class BasicController extends AbstractController {

    public BasicController(String prefix) {
        super();
        pid = Configuration.getPid(prefix + "." + PAR_NAME);
        // Read Constants

        // printParams();

    }

    /**
     * Passes the action to send to the respective worker to the BasicController
     *
     * @param action the action tobe sent, should consist of an index in the node's neighbourhood
     * @return the simulation data after applying the action, or null in case the action is invalid.
     */
    @Override
    public SimulationData sendAction(Action action) {
        if(!(action instanceof BasicAction a)) throw new RuntimeException("Wrong Class of Action being used.");

        if (!active || a == null || a.controllerId() < 0 || a.neighbourIndex() < 0) {
            return new FailledActionSimulationData(this.getId(), false);
        }
        int neigh = a.neighbourIndex();
        Linkable l = (Linkable) Network.get(a.controllerId()).getProtocol(FastConfig.getLinkable(Controller.getPid()));
        if(neigh < 0 || neigh >= l.degree()){
            ctrErrLog("An action failed because the specified index of the neighbourhood is out of bounds");
            return new FailledActionSimulationData(this.getId(), false);

        }
        if(!l.getNeighbor(neigh).isUp()){
            ctrErrLog("An action failed because the node of the specified index of the neighbourhood is down");
            return new FailledActionSimulationData(this.getId(), false);

        }

        ctrDbgLog(a.toString());
        Node node = Network.get(this.getId());
        int linkableID = FastConfig.getLinkable(Controller.getPid());
        Linkable linkable = (Linkable) node.getProtocol(linkableID);
        if (linkable.degree() <= 0 || a.neighbourIndex() >= linkable.degree()) return null;
        int neighbourIndex = a.neighbourIndex();
        ctrInfoLog(EVENT_SEND_ACTION_RECIEVED, "TARGET_INDEX=" + neighbourIndex);
        double reward = 0; //calculatReward(linkable, node, targetNode, 1);
        this.currentInstructions = new BasicOffloadInstructions(neighbourIndex);
        boolean success = this.correspondingWorker.offloadInstructions(Worker.getPid(), this.currentInstructions);
        // allow progress
        stop = false;
        return this.compileSimulationData(neighbourIndex, this.getId(), success);
    }


    //======================================================================================================
    // Private Methods
    //======================================================================================================

    //=== Node Management


    //=== Reward Function
    @Override
    public BasicSimulationData compileSimulationData(Object nI, int sourceID, boolean success){

        int neighbourIndex = (int) nI;
        int srcLinkableId = FastConfig.getLinkable(Worker.getPid());
        Linkable srcLinkable = (Linkable) Network.get(sourceID).getProtocol(srcLinkableId);

        SDNNodeProperties propsNode = (SDNNodeProperties) Network.get(sourceID).getProtocol(SDNNodeProperties.getPid());
        SDNNodeProperties propsTarget = (SDNNodeProperties) srcLinkable.getNeighbor(neighbourIndex).getProtocol(SDNNodeProperties.getPid());

        double d_i_j = Math.sqrt(Math.pow(propsNode.getY() - propsTarget.getY(), 2) + Math.pow(propsNode.getX() - propsTarget.getX(), 2));

        return new BasicSimulationData(sourceID, d_i_j, this.getWorkerInfo().get(neighbourIndex), this.extractCompletedTasks(), success);
    }


    //=== Logging and Debugging
    /*
    private void printParams() {
        //if(active)
        // ctrDbgLog("BasicController Params: r_u<" + this.UTILITY_REWARD + "> X_d<" + this.DELAY_WEIGHT + "> X_o<" + this.OVERLOAD_WEIGHT + ">");
    }
    */

    //======================================================================================================
    // Interface Methods


}