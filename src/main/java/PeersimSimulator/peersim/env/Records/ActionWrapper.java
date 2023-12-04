package PeersimSimulator.peersim.env.Records;

public class ActionWrapper<T extends Action> {

    private T action;

    public T getAction() {
        return action;
    }

    public void setAction(T action) {
        this.action = action;
    }
}
