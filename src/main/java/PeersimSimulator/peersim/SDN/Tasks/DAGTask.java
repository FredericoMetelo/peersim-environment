package PeersimSimulator.peersim.SDN.Tasks;

import java.util.Map;
import java.util.Set;

public class DAGTask extends ITask{

    private int deadline;

    private Set<Vertice> tasks;
    private Map<Vertice, Vertice> dependencies;

    private List<Vertice> orderedList;

    public DAGTask(double sizeBytes, double totalInstructions, int originNodeId, int processingNodeId, int deadline, Set<Vertice> tasks, Map<Vertice, Vertice> dependencies) {
        super(sizeBytes, totalInstructions, originNodeId, processingNodeId);
        this.deadline = deadline;
        this.tasks = tasks;
        this.dependencies = dependencies;
        this.generateExecutionOrder();
    }

    private void generateExecutionOrder(){
       //TODO
    }

    @Override
    public double addProgress(double cycles) {
        // TODO
        return 0;
    }

    private class Vertice{
        private int id;
        private int progress;
        private int totalCycles;

        public Vertice(int id){
            this.id = id;
        }

        public int getProgress() {
            return progress;
        }

        public int addProgress(int cycles){
            int left =Math.max(0, this.progress + cycles - this.totalCycles);
            this.progress += (cycles - left);
            return left;
        }

        // TODO: Add method to convert the sub-task to an ITask

        public int getId() {
            return id;
        }
    }

}
