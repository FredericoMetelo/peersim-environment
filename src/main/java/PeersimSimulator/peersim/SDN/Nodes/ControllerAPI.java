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

@RestController
public class ControllerAPI  implements Control {

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    public ControllerAPI(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @GetMapping("/state")
    public Information getState(){
        Controller c = (Controller) Network.get(0).getProtocol(Controller.getPid());
        while(!c.isStable()) Thread.onSpinWait(); // await the 100 ticks.
        return new Information(c.getState(), CommonState.getEndTime() == CommonState.getTime() + 1, c.getDebugInfo());
    }

    @PostMapping("/action")
    public double postAction(@RequestBody Action a){
        Controller c = (Controller) Network.get(0).getProtocol(Controller.getPid());
        return c.sendAction(a);
    }

    @GetMapping("/up")
    public boolean isUp(){
        Controller c = (Controller) Network.get(0).getProtocol(Controller.getPid());
        return c.isUp();
    }

    @Override
    public boolean execute() {
        return false;
    }

    private record Information(EnvState state, boolean done, DebugInfo info){}
}
