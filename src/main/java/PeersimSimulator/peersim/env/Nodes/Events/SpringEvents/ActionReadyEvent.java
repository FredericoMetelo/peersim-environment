package PeersimSimulator.peersim.env.Nodes.Events.SpringEvents;

import org.springframework.context.ApplicationEvent;

public class ActionReadyEvent extends ApplicationEvent {
    public ActionReadyEvent(Object source) {
        super(source);
    }
}