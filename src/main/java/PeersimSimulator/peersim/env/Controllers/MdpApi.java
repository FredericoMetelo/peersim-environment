package PeersimSimulator.peersim.env.Controllers;

import PeersimSimulator.peersim.env.Records.Action;
import PeersimSimulator.peersim.env.Records.DebugInfo;
import PeersimSimulator.peersim.env.Records.EnvState;
import PeersimSimulator.peersim.env.Records.SimulationData;
import PeersimSimulator.peersim.core.CommonState;
import PeersimSimulator.peersim.core.Control;
import PeersimSimulator.peersim.core.Network;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class MdpApi implements Control {

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    Information lastInfo;

    public MdpApi(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
        lastInfo = null;
    }

    @GetMapping("/state")
    public Information getState() {
        DiscreteTimeStepManager c = (DiscreteTimeStepManager) Network.get(0).getProtocol(DiscreteTimeStepManager.getPid());
        if (CommonState.POST_SIMULATION == CommonState.getPhase()) {
            return new Information(lastInfo.state(), true, lastInfo.info());
        }
        while (!c.isStable()) Thread.onSpinWait(); // await the 100 ticks.
        Information i = new Information(c.getPartialStates(), CommonState.getEndTime() <= CommonState.getTime() + c.CYCLE_SIZE, c.getDebugInfo());
        this.lastInfo = i;
        return i;
    }

    @GetMapping("/done")
    public boolean isDone(){
        DiscreteTimeStepManager c = (DiscreteTimeStepManager) Network.get(0).getProtocol(DiscreteTimeStepManager.getPid());
        return CommonState.getEndTime() <= CommonState.getTime() + c.CYCLE_SIZE;
    }
    @PostMapping("/action")
    public List<SimulationData> postAction(@RequestBody List<Action> a){

        DiscreteTimeStepManager c = (DiscreteTimeStepManager) Network.get(0).getProtocol(DiscreteTimeStepManager.getPid());

        return c.sendAction(a);
    }

    @GetMapping("/up")
    public boolean isUp(){
        if(Network.get(0) == null) return false;
        DiscreteTimeStepManager c = (DiscreteTimeStepManager) Network.get(0).getProtocol(DiscreteTimeStepManager.getPid());
        return !(CommonState.getPhase() == CommonState.POST_SIMULATION) && c.isUp();
    }



    @Override
    public boolean execute() {

        return false;
    }



    private record Information(List<EnvState> state, boolean done, DebugInfo info){}
}
