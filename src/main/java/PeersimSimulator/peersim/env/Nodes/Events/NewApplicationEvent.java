package PeersimSimulator.peersim.env.Nodes.Events;

import PeersimSimulator.peersim.env.Tasks.Application;

public class NewApplicationEvent implements Message {


    String appID;

    // int handlerID;
    int clientID;

    double initialInputSize;

    Application app;

    public NewApplicationEvent(String appID, int clientID, double initialInputSize, Application app) {
        this.appID = appID;
        this.clientID = clientID;
        this.initialInputSize = initialInputSize;
        this.app = app;
    }

    public String getAppID() {
        return appID;
    }

    public int getClientID() {
        return clientID;
    }

    public double getInitialInputSize() {
        return initialInputSize;
    }

    public Application getApp() {
        return app;
    }

    @Override
    public double getSize() {
        return initialInputSize;
    }
}
