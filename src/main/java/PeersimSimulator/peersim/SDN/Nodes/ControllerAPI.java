package PeersimSimulator.peersim.SDN.Nodes;

import PeersimSimulator.peersim.SDN.Records.Action;
import PeersimSimulator.peersim.SDN.Records.DebugInfo;
import PeersimSimulator.peersim.SDN.Records.EnvState;
import PeersimSimulator.peersim.core.CommonState;
import PeersimSimulator.peersim.core.Control;
import PeersimSimulator.peersim.core.Network;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedList;
import java.util.List;

@RestController
public class ControllerAPI  implements Control {

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    Information lastInfo;

    public ControllerAPI(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
        lastInfo = null;
    }

    @GetMapping("/state")
    public Information getState() {
        Controller c = (Controller) Network.get(0).getProtocol(Controller.getPid());
        if (CommonState.POST_SIMULATION == CommonState.getPhase()) {
            return new Information(lastInfo.state(), true, lastInfo.info());
        }
        while (!c.isStable()) Thread.onSpinWait(); // await the 100 ticks.
        Information i = new Information(c.getState(), CommonState.getEndTime() <= CommonState.getTime() + c.CYCLE_SIZE, c.getDebugInfo());
        this.lastInfo = i;
        return i;
    }

    @PostMapping("/action")
    public double postAction(@RequestBody Action a){

        Controller c = (Controller) Network.get(0).getProtocol(Controller.getPid());
        return c.sendAction(a);
    }

    @GetMapping("/up")
    public boolean isUp(){
        Controller c = (Controller) Network.get(0).getProtocol(Controller.getPid());
        return !(CommonState.getPhase() == CommonState.POST_SIMULATION) && c.isUp();
    }



    @Override
    public boolean execute() {

        return false;
    }



    private record Information(EnvState state, boolean done, DebugInfo info){}
}
