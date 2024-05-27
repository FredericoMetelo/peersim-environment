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
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class DiscreteTimeStepManager extends AbstractTimeStepManager {

    /////////////////////////////////////////////////////////////////////////
    // Constants
    /////////////////////////////////////////////////////////////////////////


    /////////////////////////////////////////////////////////////////////////
    // Fields
    /////////////////////////////////////////////////////////////////////////


    /////////////////////////////////////////////////////////////////////////
    // Constructor
    /////////////////////////////////////////////////////////////////////////
    public DiscreteTimeStepManager(String prefix) {
        super(prefix);

    }
    public void nextCycle(Node node, int protocolID) {
        if (!active) {
            return;
        }
        up = true;
        if (CommonState.getTime() % CYCLE_SIZE == 0) {
            stop = true;
            awaitAction();
        }
    }
    @Override
    public List<SimulationData> sendAction(List<Action> actionList){
        mngDbgLog(actionList.toString());
        List<SimulationData> results = new ArrayList<>(actionList.size());
        if(actionList.size() != controllerIDs.size()) {
            mngDbgLog("Partial-action, only a sub-set of the agents will be able to take the action.");
//            mngErrLog("Illegal number of Actions in joint-Action. Not progressing this iteration.");
//            return null;
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


    protected void awaitAction() {
        mngDbgLog("Start Waiting Time<" + CommonState.getTime() + ">");
        while (stop) Thread.onSpinWait();
    }

}
