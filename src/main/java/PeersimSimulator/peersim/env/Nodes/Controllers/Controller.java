package PeersimSimulator.peersim.env.Nodes.Controllers;

import PeersimSimulator.peersim.cdsim.CDProtocol;
import PeersimSimulator.peersim.core.Node;
import PeersimSimulator.peersim.edsim.EDProtocol;
import PeersimSimulator.peersim.env.Links.SDNNodeProperties;
import PeersimSimulator.peersim.env.Nodes.Events.OffloadInstructions;
import PeersimSimulator.peersim.env.Nodes.Events.WorkerInfo;
import PeersimSimulator.peersim.env.Nodes.Workers.Worker;
import PeersimSimulator.peersim.env.Records.Action;
import PeersimSimulator.peersim.env.Records.DebugInfo;
import PeersimSimulator.peersim.env.Records.PartialState;
import PeersimSimulator.peersim.env.Tasks.ITask;

import java.util.List;

public interface Controller extends CDProtocol, EDProtocol {
    String EVENT_WORKER_INFO_UPDATE = "WORKER-INFO UPDATE";
    String EVENT_WORKER_INFO_ADD = "WORKER-INFO ADD";

    static int getPid() {
        return BasicController.getPid();
    }

    @Override
    Object clone();

    @Override
    void nextCycle(Node node, int protocolID);

    @Override
    void processEvent(Node node, int pid, Object event);

    double sendAction(Action a);

    List<ITask> extractCompletedTasks();

    Worker getCorrespondingWorker();

    void initializeWorkerInfo(Node node, int protocolID);

    void setCorrespondingWorker(Worker correspondingWorker);

    OffloadInstructions requestOffloadInstructions();
    //======================================================================================================

    List<WorkerInfo> getWorkerInfo();

    boolean isActive();

    void setActive(boolean active);

    int getId();



    void setId(int id);

    List<Integer> getQ();

    PartialState getState();

    DebugInfo getDebugInfo();

    @Override
    String toString();

    void setProps(SDNNodeProperties prot);
}
