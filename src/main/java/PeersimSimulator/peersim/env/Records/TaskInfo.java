package PeersimSimulator.peersim.env.Records;

import PeersimSimulator.peersim.env.Tasks.ITask;

public class TaskInfo {
    protected double progress;
    protected final double totalInstructions;
    protected final double outputSizeBytes;
    protected final double inputSizeBytes;
    protected final String id;
    protected boolean processedLocally;
    public TaskInfo(ITask task, boolean processedLocally) {
        this.progress = task.getProgress();
        this.totalInstructions = task.getTotalInstructions();
        this.outputSizeBytes = task.getOutputSizeBytes();
        this.inputSizeBytes = task.getInputSizeBytes();
        this.id = task.getId();
        this.processedLocally = processedLocally;
    }

    public TaskInfo(double progress, double totalInstructions, double outputSizeBytes, double inputSizeBytes, String id, boolean processedLocally) {
        this.progress = progress;
        this.totalInstructions = totalInstructions;
        this.outputSizeBytes = outputSizeBytes;
        this.inputSizeBytes = inputSizeBytes;
        this.id = id;
        this.processedLocally = processedLocally;
    }

    public double getProgress() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress = progress;
    }

    public double getTotalInstructions() {
        return totalInstructions;
    }

    public double getOutputSizeBytes() {
        return outputSizeBytes;
    }

    public double getInputSizeBytes() {
        return inputSizeBytes;
    }

    public String getId() {
        return id;
    }

    public boolean isProcessedLocally() {
        return processedLocally;
    }

    public void setProcessedLocally(boolean processedLocally) {
        this.processedLocally = processedLocally;
    }
}

