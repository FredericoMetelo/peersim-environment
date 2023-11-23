package PeersimSimulator.peersim.env.Tasks;

import java.util.ArrayList;
import java.util.List;

public class TaskHistory {


    public enum TaskEvenType {
        SELECTED_FOR_PROCESSING,
        OFFLOADED,
        COMPLETED,
        CREATED,
        DROPPED


    }

    public record TaskEvent(
            TaskEvenType type,
            int nodeId,
            double time
    ) {
    }

    List<TaskEvent> events;

    public TaskHistory() {
        this.events = new ArrayList<>();
    }

    public void created(int nodeId, double time) {
        this.events.add(new TaskEvent(TaskEvenType.CREATED, nodeId, time));
    }

    public void offloaded(int nodeId, double time) {
        this.events.add(new TaskEvent(TaskEvenType.OFFLOADED, nodeId, time));
    }

    public void selectedForProcessing(int nodeId, double time) {
        this.events.add(new TaskEvent(TaskEvenType.SELECTED_FOR_PROCESSING, nodeId, time));
    }
    public void completed(int nodeId, double time) {
        this.events.add(new TaskEvent(TaskEvenType.COMPLETED, nodeId, time));
    }
    public void dropped(int nodeId, double time) {
        this.events.add(new TaskEvent(TaskEvenType.DROPPED, nodeId, time));
    }

}