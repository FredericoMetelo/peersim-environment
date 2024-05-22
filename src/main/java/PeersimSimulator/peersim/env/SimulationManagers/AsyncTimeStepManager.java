package PeersimSimulator.peersim.env.SimulationManagers;

import PeersimSimulator.peersim.core.CommonState;
import PeersimSimulator.peersim.core.Network;
import PeersimSimulator.peersim.core.Node;
import PeersimSimulator.peersim.env.Nodes.Controllers.Controller;
import PeersimSimulator.peersim.env.Records.Actions.Action;
import PeersimSimulator.peersim.env.Records.SimulationData.SimulationData;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

public class AsyncTimeStepManager extends AbstractTimeStepManager {
    public AsyncTimeStepManager(String prefix) {
        super(prefix);
    }

    public void nextCycle(Node node, int protocolID) {
        if (!active) {
            return;
        }
        up = true;
        if (CommonState.getTime() % CYCLE_SIZE == 0) {
            stop = true;
//            awaitAction();
        }
    }
    @Override
    public List<SimulationData> sendAction(List<Action> actionList){
        mngDbgLog(actionList.toString());
        List<SimulationData> results = new ArrayList<>(actionList.size());
        if(actionList.size() != controllerIDs.size()) {
            mngErrLog("Illegal number of Actions in joint-Action. Not progressing this iteration.");
            return null;
        }
        for (Action a : actionList) {
            int controllerId = a.controllerId();
            if(!controllerIDs.contains(controllerId)){
                mngErrLog("An action was sent for id " + controllerId + "this id does not correspond to any controller. Ignoring action.");
            }

            Controller c = (Controller) Network.get(controllerId).getProtocol(Controller.getPid());
            if (!c.isActive()) throw new RuntimeException("Inactive BasicController id=" + controllerId);
            synchronized (this) {
                // Note two things, the actionList should only have one action. The lock could be over the specific controller.
                SimulationData result = c.sendAction(a);
                results.add(result);
            }
        }
        try{
            mngDbgLog(new ObjectMapper().writeValueAsString(results));
        } catch (Exception e) {
            e.printStackTrace();
        }
//        stop = false;
        return results;
    }

}
