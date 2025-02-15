package PeersimSimulator.peersim.env.Records;

public class AlibabaClusterJob {
    //public String jobId;
    public double criticalPathResourcesCPU;
    public double criticalPathResourcesMemory;
    public double criticalPathDuration;
    public double totalResourcesCPU;
    public double totalResourcesMemory;
    public double totalResourcesInstances;
    public double totalResourcesDuration;
    public double maxMemory;
    public double maxCPU;

    public AlibabaClusterJob(double criticalPathResourcesCPU,
                             double criticalPathResourcesMemory,
                             double totalResourcesCPU,
                             double totalResourcesMemory,
                             double totalResourcesInstances,
                             double totalResourcesDuration,
                             double criticalPathDuration,
                             double maxMemory,
                             double maxCPU
    ) {
        //this.jobId = jobId;
        this.criticalPathResourcesCPU = criticalPathResourcesCPU;
        this.criticalPathResourcesMemory = criticalPathResourcesMemory;
        this.criticalPathDuration = criticalPathDuration;
        this.totalResourcesCPU = totalResourcesCPU;
        this.totalResourcesMemory = totalResourcesMemory;
        this.totalResourcesInstances = totalResourcesInstances;
        this.totalResourcesDuration = totalResourcesDuration;
        this.maxMemory = maxMemory;
        this.maxCPU = maxCPU;
    }

    public double getCriticalPathResourcesCPU() {
        return criticalPathResourcesCPU;
    }

    public double getCriticalPathResourcesMemory() {
        return criticalPathResourcesMemory;
    }

    public double getCriticalPathDuration() {
        return criticalPathDuration;
    }

    public double getTotalResourcesDuration() {
        return totalResourcesDuration;
    }

    public double getTotalResourcesCPU() {
        return totalResourcesCPU;
    }

    public double getTotalResourcesMemory() {
        return totalResourcesMemory;
    }

    public double getTotalResourcesInstances() {
        return totalResourcesInstances;
    }

    public double getMaxMemory() {
        return maxMemory;
    }

    public double getMaxCPU() {
        return maxCPU;
    }

    @Override
    public String toString() {
        return "Job{" +
                //"jobId='" + jobId + '\'' +
                ", criticalPathResourcesCPU=" + criticalPathResourcesCPU +
                ", criticalPathResourcesMemory=" + criticalPathResourcesMemory +
                ", totalResourcesCPU=" + totalResourcesCPU +
                ", totalResourcesMemory=" + totalResourcesMemory +
                ", totalResourcesInstances=" + totalResourcesInstances +
                '}';
    }
}
