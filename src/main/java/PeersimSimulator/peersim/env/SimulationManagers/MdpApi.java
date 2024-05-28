package PeersimSimulator.peersim.env.SimulationManagers;

import PeersimSimulator.peersim.env.Nodes.Controllers.Controller;
import PeersimSimulator.peersim.env.Records.*;
import PeersimSimulator.peersim.core.CommonState;
import PeersimSimulator.peersim.core.Control;
import PeersimSimulator.peersim.core.Network;
import PeersimSimulator.peersim.env.Records.Actions.Action;
import PeersimSimulator.peersim.env.Records.SimulationData.BasicSimulationData;
import PeersimSimulator.peersim.env.Records.SimulationData.BatchSimulationData;
import PeersimSimulator.peersim.env.Records.SimulationData.SimulationData;
import PeersimSimulator.peersim.env.Util.Log;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.*;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;

import java.util.LinkedList;
import java.util.List;

@RestController
public class MdpApi implements Control {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @PostConstruct
    public void registerSubtypes() {
        MAPPER.registerSubtypes(new NamedType(PeersimSimulator.peersim.env.Records.SimulationData.BatchSimulationData.class, "batchSD"));
        MAPPER.registerSubtypes(new NamedType(PeersimSimulator.peersim.env.Records.SimulationData.BasicSimulationData.class, "basicSD"));
    }

    Information lastInfo;

    public MdpApi() {
        lastInfo = null;
    }


    // MDP Endpoints ...................................................................................................

    @GetMapping("/state")
    public Information getState() {
        System.out.println("Getting state");
        DiscreteTimeStepManager c = (DiscreteTimeStepManager) Network.get(0).getProtocol(DiscreteTimeStepManager.getPid());
        if (CommonState.POST_SIMULATION == CommonState.getPhase()) {
            return new Information(lastInfo.state(), true, lastInfo.info());
        }
        while (!c.isStable()) Thread.onSpinWait(); // await the 100 ticks.
        State gs = c.getState();
        Information i = new Information(c.getState(), CommonState.getEndTime() <= CommonState.getTime() + c.CYCLE_SIZE, c.getDebugInfo());
        this.lastInfo = i;
        return i;
    }

    @GetMapping("/done")
    public boolean isDone(){
        DiscreteTimeStepManager c = (DiscreteTimeStepManager) Network.get(0).getProtocol(DiscreteTimeStepManager.getPid());
        return CommonState.getEndTime() <= CommonState.getTime() + c.CYCLE_SIZE;
    }
    @PostMapping("/action")
    public List<SimulationData> postAction(@RequestBody List<Action> a) {

        AbstractTimeStepManager dtm = (AbstractTimeStepManager) Network.get(0).getProtocol(AbstractTimeStepManager.getPid());
        List<SimulationData> lsd = dtm.sendAction(a);

        return lsd;

    }
    @PostMapping("/forward")
    public List<SimulationData> forwardPost(@RequestBody List<Action> a) {
        AbstractTimeStepManager dtm = (AbstractTimeStepManager) Network.get(0).getProtocol(AbstractTimeStepManager.getPid());
        List<SimulationData> lsd = dtm.forward(a);

        return lsd;
    }
    // FL Endpoints ....................................................................................................

    @PostMapping("/fl/update")
    public boolean postUpdates(@RequestBody List<FLUpdate> updates){
        // Request the controller with the given id to send the updates through the network.
        boolean worked = true;
        for (int i = 0; i < updates.size(); i++) {
            FLUpdate update = updates.get(i);
            int src = update.getSrc();
            if(Network.get(src) == null) {
                Log.err("Node with id " + src + " does not exist.");
                return false;
            }
            Controller c = (Controller) Network.get(src).getProtocol(Controller.getPid());
            if(!c.isActive())
                return false;
            worked = worked && c.sendFLUpdate(update);
        }
        return worked;
    }
    @GetMapping("/fl/done")
    public List<String> finishedFLUpdates(){
        List<String> finished = new LinkedList<>();
        for (int i = 0; i < Network.size(); i++) {
            Controller c = (Controller) Network.get(i).getProtocol(Controller.getPid());
            if(c.isActive()) {
                finished.addAll(c.getUpdatesAvailable());
            }

        }
        return finished;
    }

    // Support Endpoints ...............................................................................................

    @GetMapping("/up")
    public boolean isUp(){
        if(Network.get(0) == null) return false;
        System.err.println("Checking up");
        AbstractTimeStepManager c = (AbstractTimeStepManager) Network.get(0).getProtocol(AbstractTimeStepManager.getPid());
        return !(CommonState.getPhase() == CommonState.POST_SIMULATION) && c.isUp();
    }

    @GetMapping("/stopped")
    public boolean isStopped(){
        if(Network.get(0) == null) return false;
        System.err.println("Checking stopped");
        AbstractTimeStepManager c = (AbstractTimeStepManager) Network.get(0).getProtocol(AbstractTimeStepManager.getPid());
        return !(CommonState.getPhase() == CommonState.POST_SIMULATION) && !c.isStable();
    }

    @GetMapping("/NeighbourData")
    public NetworkData getNeighbourData(){
        if(Network.get(0) == null) return null;
        System.err.println("Checking neighbourData");
        AbstractTimeStepManager c = (AbstractTimeStepManager) Network.get(0).getProtocol(AbstractTimeStepManager.getPid());
        return c.getNeighbourData();
    }

    @Override
    public boolean execute() {

        return false;
    }



    private record Information(State state, boolean done, DebugInfo info){}
}
