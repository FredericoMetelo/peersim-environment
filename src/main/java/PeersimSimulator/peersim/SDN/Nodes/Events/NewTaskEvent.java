package PeersimSimulator.peersim.SDN.Nodes.Events;

import PeersimSimulator.peersim.SDN.Tasks.Task;

public class NewTaskEvent  implements Message{
    Task task;

    public NewTaskEvent(Task task) {
        this.task = task;
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }

    @Override
    public double getSize() {
        return task.getSizeBytes();
    }
}
