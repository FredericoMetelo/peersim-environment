package PeersimSimulator.peersim.SDN.Tasks;

import PeersimSimulator.peersim.SDN.Util.Log;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class Application {


    private Map<String, Vertice> tasks;
    private Map<String, List<Vertice>> dependencies;

    private Set<Vertice> finishedSubtasks;


    private int deadline;

    private String appID;

    private String clientID;

    private String handlerID;

    private int aggregateTaskSize;

    private int aggregateProgress;

    private int initialDataSize;


    public Application(Map<String, Vertice> tasks, Map<String, List<Vertice>> dependencies, int deadline, String appID, String clientID, int initialDataSize) {
        this.tasks = tasks;
        this.dependencies = dependencies;
        this.deadline = deadline;
        this.appID = appID;
        this.clientID = clientID;
        this.initialDataSize = initialDataSize;

        for(String v : tasks.keySet()) {
            aggregateTaskSize += tasks.get(v).getTotalInstructions();
        }

        aggregateProgress = 0;


    }

    private void generateExecutionOrder(){
       //TODO
    }

    public boolean subTaskCanAdvance(String task){
        if(!dependencies.containsKey(task)) {
            Log.err("Somehow we are checking the wrong application for a subtask! AppID" + this.appID + " TaskID: " + task );
            return false;
        }
        List<Vertice> d = dependencies.get(task);
        for(Vertice v: d){

        }
        return true;

    }

    public void addProgress(String subTaskFinished){
        Vertice v = this.tasks.get(subTaskFinished);
        this.aggregateProgress += v.getTotalInstructions();
        finishedSubtasks.add(v);
    }

    private class Vertice extends ITask{
        private int id;

        public Vertice(double sizeBytes, double totalInstructions, int originNodeId, int processingNodeId, int id) {
            super(sizeBytes, totalInstructions, originNodeId, processingNodeId);
            this.id = id;
            this.progress = 0;
        }


        public int getId() {
            return id;
        }

        @Override
        public double addProgress(double cycles) {
            double total_cycles = this.progress + cycles;
            this.progress = Math.min(total_cycles, this.totalInstructions);
            return Math.max(0, total_cycles - this.totalInstructions);
        }
    }

}
