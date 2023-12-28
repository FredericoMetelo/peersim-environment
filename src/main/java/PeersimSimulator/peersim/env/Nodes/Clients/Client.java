package PeersimSimulator.peersim.env.Nodes.Clients;

import PeersimSimulator.peersim.cdsim.CDProtocol;
import PeersimSimulator.peersim.core.Node;
import PeersimSimulator.peersim.edsim.EDProtocol;
import PeersimSimulator.peersim.env.Tasks.Application;

public interface Client extends CDProtocol, EDProtocol {

    // PARAMETER NAMES ==========================================================
    String EVENT_TASK_CONCLUDED = "TASK CONCLUDED";
    String EVENT_TASK_SENT = "TASK SENT";
    String PAR_TASKARRIVALRATE = "taskArrivalRate";
    String PAR_NO_TASKS = "numberOfTasks";
    String PAR_NO_INSTR = "I";
    String PAR_TASKS_WEIGHTS = "weight";
    String PAR_MAX_DEADLINE = "maxDeadline";
    String PAR_CPI = "CPI";

    String PAR_NAME = "name";
    String PAR_BYTE_SIZE = "T";

    // DEFAULT VALUES ==========================================================

    String DEFAULT_TASK_SIZE = "500";
    String DEFAULT_NO_INSTR = "200e6";
    double DEFAULT_TASKARRIVALRATE = 0.01;
    int DEFAULT_NUMBEROFTASKS = 1;
    int DEFAULT_NUMBEROFDAG = 1;
    int DEFAULT_MAX_DEADLINE = 50;


    // METHODS =================================================================
    static int getPid() {
        return AbstractClient.pid;
    }

    @Override
    Object clone();

    @Override
    void nextCycle(Node node, int protocolID);

    /**
     * IMPORTANT FOR THE CONFIGS!!!!!
     * Task 0 must never have predecessors (aka, be the first task),
     * the last index task must not have any successors (aka be the last task)
     *
     * @param target
     * @return
     */
    Application generateApplication(int target);

    @Override
    void processEvent(Node node, int pid, Object event);

    boolean isActive();

    double getAverageTaskSize();

    void setActive(boolean active);

    int getId();

    void setId(int id);

    double getAverageByteSize();

    double getAverageTaskCompletionTime();
    @Override
    String toString();
}
