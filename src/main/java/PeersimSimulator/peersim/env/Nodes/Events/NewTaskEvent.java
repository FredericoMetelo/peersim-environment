package PeersimSimulator.peersim.env.Nodes.Events;

import PeersimSimulator.peersim.env.Tasks.ITask;

public class NewTaskEvent  implements Message{
    ITask task;

    public NewTaskEvent(ITask task) {
        this.task = task;
    }

    public ITask getTask() {
        return task;
    }

    public void setTask(ITask task) {
        this.task = task;
    }

    @Override
    public double getSize() {
        return task.getInputSizeBytes();
    }
}
