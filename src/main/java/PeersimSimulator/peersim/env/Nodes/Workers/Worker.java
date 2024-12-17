package PeersimSimulator.peersim.env.Nodes.Workers;

import PeersimSimulator.peersim.cdsim.CDProtocol;
import PeersimSimulator.peersim.edsim.EDProtocol;
import PeersimSimulator.peersim.env.Links.SDNNodeProperties;
import PeersimSimulator.peersim.env.Nodes.Controllers.Controller;
import PeersimSimulator.peersim.env.Nodes.Events.OffloadInstructions;
import PeersimSimulator.peersim.env.Nodes.Events.WorkerInfo;
import PeersimSimulator.peersim.env.Records.FLUpdate;
import PeersimSimulator.peersim.env.Tasks.ITask;

import java.util.List;

public interface Worker extends CDProtocol, EDProtocol {
    //======================================================================================================
    // Constants/Immutable values
    //======================================================================================================
    String PAR_NAME = "name";
    int RANK_EVENT_DELAY = 2;
    String PAR_MAX_TIME_AFTER_DEADLINE = "maxTimeAfterDeadline";
    String PAR_ENERGY_COST_COMM = "energyCostComm";
    String PAR_ENERGY_COST_COMP = "energyCostComp";

    int DEFAULT_TIME_AFTER_DEADLINE = 5;
    String EVENT_WORKER_INFO_SEND = "WRK-INFO BROADCAST";
    String EVENT_TASK_FINISH = "TASK FINISH";
    String EVENT_APP_FINISH = "APP FINISH";
    String EVENT_OFFLOADED_TASKS_ARRIVED_AT_WRONG_NODE = "Offloaded Tasks Arrived at Wrong Node";
    String EVENT_TASK_OFFLOAD_RECIEVE = "TASK OFFLOAD RECIEVE";
    String EVENT_OVERLOADED_NODE = "OVERLOADED NODE";
    String EVENT_NEW_APP_RECIEVED = "NEW APP RECIEVED";
    String EVENT_ERR_NODE_OUT_OF_BOUNDS = "NODE OUT OF BOUNDS";
    String EVENT_WORKER_INFO_SEND_FAILED = "WRK-INFO SEND FAIL";
    String EVENT_NO_TASK_PROCESS = "NO TASK FOR PROCESS";
    String EVENT_NO_TASK_OFFLOAD = "NO TASK FOR OFFLOAD";
    String EVENT_ERR_NO_TARGET_PID_AVAILABLE = "NO TARGET PID ACTIVE";
    String EVENT_ERR_NO_PATH = "FL-NPTH";
    String EVENT_ERR_NO_DST = "NO-DST";
    String EVENT_OFF_TASK_EXPIRED = "OFFLOADED TASK EXPIRED";
    String EVENT_APPS_EXPIRED = "APPS EXPIRED";

    static int getPid() {
        return AbstractWorker.pid;
    }

    @Override
    Object clone();

    void workerInit(double cpuFreq, int noCores, int qMax, int layer);

    @Override
    void nextCycle(PeersimSimulator.peersim.core.Node node, int protocolID);

    @Override
    void processEvent(PeersimSimulator.peersim.core.Node node, int pid, Object event);

    boolean offloadInstructions(int pid, OffloadInstructions oi);

    WorkerInfo compileWorkerInfo();

    //======================================================================================================
    // Getter and Setter Methods
    //======================================================================================================
    @Override
    String toString();

    int getTotalNumberOfTasksInNode();

    void setActive(boolean active);

    int unprocessedTasksInApps();

    boolean isActive();

    void setId(int id);

    int getId();

    SDNNodeProperties getProps();

    void setProps(SDNNodeProperties props);

    void setProcessingPower(double processingPower);

    double getProcessingPower();

    void setCorrespondingController(Controller correspondingController);

    void setHasController(boolean hasController);

    void setCloudAccess(int cloudAccess);

    boolean isHasController();

    int getDroppedLastCycle();

    int getTotalDropped();

    int getTotalTasksRecieved();

    int getLayer();

    int getCloudAccess();

    int getTasksRecievedSinceLastCycle();

    int getTotalTasksProcessed();

    int getTotalTasksOffloaded();

    void wrkInfoLog(String event, String info);

    List<ITask> extractCompletedTasks();

    void wrkDbgLog(String msg);

    void wrkErrLog(String event, String msg);

    int getCpuNoCores();

    double getAverageWaitingTime();

    double getEnergyConsumed();
    void setEnergyConsumed(double energyConsumed);

    double getCpuFreq();

    int getTimesOverloaded();

    int failedOnArrivalToNode();

    int getNoExpiredTasks();

    int getQueueCapacity();

    int isWorking();

    boolean sendFLUpdate(FLUpdate update);

    int getTotalReceivedTasks();
}
