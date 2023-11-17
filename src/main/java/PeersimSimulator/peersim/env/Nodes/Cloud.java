package PeersimSimulator.peersim.env.Nodes;

import PeersimSimulator.peersim.cdsim.CDProtocol;
import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.core.Node;
import PeersimSimulator.peersim.edsim.EDProtocol;
import PeersimSimulator.peersim.env.Tasks.ITask;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class Cloud  implements CDProtocol, EDProtocol {


    public static final String PAR_NO_VM = "no_vms";
    private static final String PAR_NAME = "name";
    public static final String PAR_VMPROCESSING_POWER = "VMProcessingPower";


    public int noVms;

    /**
     * The cloud internal id on the network
     */
    private int id;


    /**
     * Protocol identifier, obtained from config property {@link #PAR_NAME}.
     */
    private static int pid;
    private double defaultProcessingPower;
    private Queue<ITask> queue;
    private List<VM> vms;

    public Cloud(String prefix){
        pid = Configuration.getPid(prefix + "." + PAR_NAME);
        noVms = Configuration.getInt(prefix + "."+PAR_NO_VM, 1);
        defaultProcessingPower =  Configuration.getDouble(prefix + "." + PAR_VMPROCESSING_POWER);


        init();
    }
    @Override
    public void nextCycle(Node node, int protocolID) {
        for (VM vm : this.vms){
            ITask t;
            vm.resetProcessingPower();

            do{
                if(vm.idle()) {
                    ITask next = this.selectNext();
                    vm.attributeTask(next);
                }
                t = vm.process();
                if(t != null){
                    this.handleFinishedTask(t);
                }
            }while(t != null);
        }

    }

    private void handleFinishedTask(ITask t) {
    }

    @Override
    public Object clone() {
        Cloud svh = null;
        try {
            svh = (Cloud) super.clone();
            svh.init();
        } catch (CloneNotSupportedException e) {
        } // never happens
        return svh;
    }
    @Override
    public void processEvent(Node node, int pid, Object event) {

    }

    private void init(){
        queue = new LinkedList<>();
        vms = new ArrayList<>();
        for (int i = 0; i < noVms; i++) {
            vms.add(new VM(defaultProcessingPower));
        }
    }

    private ITask selectNext(){
       return queue.poll();
    }

    private class VM {
        ITask current;
        double processingPower;

        double processingPowerLeft;
        VM(double processingPower){
            this.processingPower = processingPower;
            this.processingPowerLeft = 0;
            this.current = null;
        }

        ITask process(){
            double remainingProcessingPower = processingPower;
            while (remainingProcessingPower > 0 && this.idle()) {
                remainingProcessingPower = current.addProgress(remainingProcessingPower);
                if (current.done()) {
                    ITask t = current;
                    current = null;
                    return t;
                }
            }
            return null;
        }

        void attributeTask(ITask t){
            this.current = t;
        }

        void resetProcessingPower(){
            processingPowerLeft = 0;
        }

        public boolean idle() {
            return this.current == null;
        }
    }
}
