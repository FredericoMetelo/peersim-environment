package PeersimSimulator.peersim.env.Nodes.Controllers;

import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.config.FastConfig;
import PeersimSimulator.peersim.core.Linkable;
import PeersimSimulator.peersim.core.Network;
import PeersimSimulator.peersim.core.Node;
import PeersimSimulator.peersim.env.Links.SDNNodeProperties;
import PeersimSimulator.peersim.env.Nodes.Events.BatchOffloadInstructions;
import PeersimSimulator.peersim.env.Nodes.Workers.Worker;
import PeersimSimulator.peersim.env.Records.Actions.Action;
import PeersimSimulator.peersim.env.Records.Actions.BatchAction;
import PeersimSimulator.peersim.env.Records.SimulationData.BatchSimulationData;
import PeersimSimulator.peersim.env.Records.SimulationData.FailledActionSimulationData;
import PeersimSimulator.peersim.env.Records.SimulationData.SimulationData;

import java.util.List;

public class BatchController extends AbstractController {

    public BatchController(String prefix) {
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
        if(!(action instanceof BatchAction a))
            throw new RuntimeException("Wrong Class of Action being used.");
        if (!active || a == null || a.controllerId() < 0) {
            return new FailledActionSimulationData(this.getId(), false);

        }
        if(a.neighbourIndexes().isEmpty()){
           return this.compileSimulationData(a.neighbourIndexes(), this.getId(), false);
        }

        List<Integer> neigh = a.neighbourIndexes();
        Linkable l = (Linkable) Network.get(a.controllerId()).getProtocol(FastConfig.getLinkable(Controller.getPid()));
        ctrDbgLog(a.toString());
        Node node = Network.get(this.getId());
        int linkableID = FastConfig.getLinkable(Controller.getPid());
        Linkable linkable = (Linkable) node.getProtocol(linkableID);
        List<Integer> neighbourIndex = a.neighbourIndexes();
        ctrInfoLog(EVENT_SEND_ACTION_RECIEVED, "TARGET_INDEX=" + neighbourIndex);


        this.currentInstructions = new BatchOffloadInstructions(neighbourIndex);
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
    public SimulationData compileSimulationData(Object nI, int sourceID, boolean success){

       List<Integer> neighbourIndex = (List<Integer>) nI;


        int srcLinkableId = FastConfig.getLinkable(Worker.getPid());
        Linkable srcLinkable = (Linkable) Network.get(sourceID).getProtocol(srcLinkableId);
        SDNNodeProperties propsNode = (SDNNodeProperties) Network.get(sourceID).getProtocol(SDNNodeProperties.getPid());

        List<Double> distance = neighbourIndex.stream().map(
                i -> {
                    SDNNodeProperties propsTarget = (SDNNodeProperties) srcLinkable.getNeighbor(i).getProtocol(SDNNodeProperties.getPid());
                    return Math.sqrt(Math.pow(propsNode.getY() - propsTarget.getY(), 2) + Math.pow(propsNode.getX() - propsTarget.getX(), 2));
                }
        ).toList();

        return new BatchSimulationData(sourceID, distance, this.extractCompletedTasks(), success);
    }

    //=== Logging and Debugging
    /*private void printParams() {
        //if(active)
        // ctrDbgLog("BasicController Params: r_u<" + this.UTILITY_REWARD + "> X_d<" + this.DELAY_WEIGHT + "> X_o<" + this.OVERLOAD_WEIGHT + ">");
    }*/

    //======================================================================================================
    // Interface Methods


}
