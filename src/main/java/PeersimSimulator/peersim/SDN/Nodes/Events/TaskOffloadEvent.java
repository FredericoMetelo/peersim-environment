package PeersimSimulator.peersim.SDN.Nodes.Events;

import PeersimSimulator.peersim.SDN.Nodes.Client;
import PeersimSimulator.peersim.SDN.Tasks.ITask;
import PeersimSimulator.peersim.SDN.Tasks.Task;

import java.util.List;
import java.util.OptionalDouble;

public class TaskOffloadEvent implements Message{
    int srcNode;
    int dstNode;

    List<ITask> taskList;

    public TaskOffloadEvent(int srcNode, int dstNode, List<ITask> taskList) {
        this.srcNode = srcNode;
        this.dstNode = dstNode;
        this.taskList = taskList;
    }

    public int getSrcNode() {
        return srcNode;
    }

    public void setSrcNode(int srcNode) {
        this.srcNode = srcNode;
    }

    public int getDstNode() {
        return dstNode;
    }

    public void setDstNode(int dstNode) {
        this.dstNode = dstNode;
    }

    public List<ITask> getTaskList() {
        return taskList;
    }

    public void setTaskList(List<ITask> taskList) {
        this.taskList = taskList;
    }

    @Override
    public double getSize() {
        return taskList.stream().mapToDouble(ITask::getSizeBytes).sum();
    }
}
