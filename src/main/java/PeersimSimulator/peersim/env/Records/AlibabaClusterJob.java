package PeersimSimulator.peersim.env.Records;

public class AlibabaClusterJob {
    //public String jobId;
    public double criticalPathResourcesCPU;
    public double criticalPathResourcesMemory;
    public double totalResourcesCPU;
    public double totalResourcesMemory;
    public double totalResourcesInstances;

    public AlibabaClusterJob(double criticalPathResourcesCPU, double criticalPathResourcesMemory, double totalResourcesCPU, double totalResourcesMemory, double totalResourcesInstances) {
        //this.jobId = jobId;
        this.criticalPathResourcesCPU = criticalPathResourcesCPU;
        this.criticalPathResourcesMemory = criticalPathResourcesMemory;
        this.totalResourcesCPU = totalResourcesCPU;
        this.totalResourcesMemory = totalResourcesMemory;
        this.totalResourcesInstances = totalResourcesInstances;
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
