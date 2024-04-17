package PeersimSimulator.peersim.env.Transport;

import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.config.FastConfig;
import PeersimSimulator.peersim.config.IllegalParameterException;
import PeersimSimulator.peersim.core.CommonState;
import PeersimSimulator.peersim.core.Linkable;
import PeersimSimulator.peersim.core.Network;
import PeersimSimulator.peersim.core.Node;
import PeersimSimulator.peersim.edsim.EDSimulator;
import PeersimSimulator.peersim.env.Links.SDNNodeProperties;
import PeersimSimulator.peersim.env.Nodes.Events.Message;
import PeersimSimulator.peersim.env.Nodes.Workers.Worker;
import PeersimSimulator.peersim.transport.Transport;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

public class MaxCapacityTransport implements Transport {
    /**
     * String name of the parameter used to configure the minimum latency.
     *
     * @config
     */
    protected static final String PAR_MINDELAY = "mindelay";
    /**
     * String name of the parameter used to configure the maximum latency.
     * Defaults to {@value #PAR_MINDELAY}, which results in a constant delay.
     *
     * @config
     */
    protected static final String PAR_MAXDELAY = "maxdelay";
    protected static final String PROPERTY_PROTOCOL = "protocol.props";
    protected static final String PAR_BANDWIDTH = "B";

    protected static final String PAR_RANDOMIZETOPOLOGY = "RANDOMIZETOPOLOGY";
    protected static final String PAR_NO_LAYERS= "NO_LAYERS";

    protected final boolean RANDOMIZETOPOLOGY;

    // ====== End of original class by peersim ======
    protected static int pid;
    protected static final String PAR_NAME = "name";
    /**
     * Minimum delay for message sending
     */
    protected final long min;
    /**
     * Difference between the max and min delay plus one. That is, max delay is
     * min+range-1.
     */
    protected final long range;
    protected final double BANDWIDTH;

    protected List<List<Integer>> channelTypePerLayer;
    protected final List<SNRCalculator> channelTypes;


    public MaxCapacityTransport(String prefix){
        /* Test helper
        protocol.urt.channelTypes PeersimSimulator.peersim.env.Transport.OpticalFiberSNR;PeersimSimulator.peersim.env.Transport.WirelessSNR
        protocol.urt.channelTypesBetweenLayers 0,-1,-1,-1,-1,-1,-1,-1,1,-1,-1,1;-1,0,-1,-1,-1,-1,-1,-1,1,-1,-1,1;-1,-1,0,-1,-1,-1,-1,-1,1,-1,-1,1;-1,-1,-1,0,-1,-1,-1,-1,1,-1,-1,1;-1,-1,-1,-1,0,-1,-1,-1,1,-1,-1,1;-1,-1,-1,-1,-1,0,-1,-1,1,-1,-1,1;-1,-1,-1,-1,-1,-1,0,-1,1,-1,-1,1;-1,-1,-1,-1,-1,-1,-1,0,1,-1,-1,1;0,0,0,0,0,0,0,0,1,1,1,1;-1,-1,-1,-1,-1,-1,-1,-1,1,1,-1,1;-1,-1,-1,-1,-1,-1,-1,-1,1,-1,1,1;0,0,0,0,0,0,0,0,1,1,1,1
         */
        pid = Configuration.getPid(prefix + "." + PAR_NAME);
        int no_layers = Configuration.getInt(PAR_NO_LAYERS);
        RANDOMIZETOPOLOGY = Configuration.getBoolean(PAR_RANDOMIZETOPOLOGY, true);
        BANDWIDTH = Configuration.getDouble( PROPERTY_PROTOCOL + "." + PAR_BANDWIDTH, 2); // 2Mhz
        min = Configuration.getLong(prefix + "." + PAR_MINDELAY);
        long max = Configuration.getLong(prefix + "." + PAR_MAXDELAY,min);
        if (max < min)
            throw new IllegalParameterException(prefix+"."+PAR_MAXDELAY,
                    "The maximum latency cannot be smaller than the minimum latency");
        range = max-min+1;

        channelTypes = getChannelSNRCalculator(Configuration.getString(prefix + ".channelTypes"), prefix);
        channelTypePerLayer = getChannelTypeBetweenLayers(Configuration.getString(prefix + ".channelTypesBetweenLayers"));
        if(channelTypePerLayer.size() != no_layers){
            throw new IllegalParameterException(prefix+"."+PAR_NO_LAYERS,
                    "The channelTypesBetweenLayers should be a square matrix of size no_layers per no_layers. Current size is " + channelTypePerLayer.size() + " per " + channelTypePerLayer.get(0).size() + "; And no_layers: "+ no_layers+".");
        }
    }

    /**
     * Returns the index in the channelType list to use for communication between each pair of layers. -1 means no
     * communication is possible.
     * @param channelTypePerLayer
     * @return
     */
    private List<List<Integer>> getChannelTypeBetweenLayers(String channelTypePerLayer) {
        List<List<Integer>> channelTypePerLayerList = new ArrayList<>();
        if(RANDOMIZETOPOLOGY){
            for(int i = 0; i< Network.size(); i++){
                List<Integer> channelTypeList = new ArrayList<>();
//                Linkable l = (Linkable) Network.get(i).getProtocol(FastConfig.getLinkable(CommonState.getPid()));
//                int l_pos = 0;
                for(int j = 0; j< Network.size(); j++){
                    channelTypeList.add(0); // In random mode, everything communicates through the same channel type.
                }
                channelTypePerLayerList.add(channelTypeList);
            }
        }
        String[] layers = channelTypePerLayer.split(";");
        for (String layer : layers) {
            String[] channelTypes = layer.split(",");
            List<Integer> channelTypeList = new ArrayList<>();
            for (String channelType : channelTypes) {
                channelTypeList.add(Integer.parseInt(channelType));
            }
            channelTypePerLayerList.add(channelTypeList);
        }
        return channelTypePerLayerList;
    }

    /**
     * Returns <code>this</code>. This way only one instance exists in the system
     * that is linked from all the nodes. This is because this protocol has no
     * node specific state.
     */
    public Object clone()
    {
        // Note: This will clone the reference to the channelTypes list. This is fine as the list is immutable.
        return this;
    }

    /**
     * Delivers the message with a delay based on Shannon-Hartley theorem
     */
    public void send(Node src, Node dest, Object msg, int pid) {
        /*
         We wish to use the Shannon-Hartley theorem to calculate the delay. We will use the following formula:

         t^c = \frac{T}{C}

         Where T [b] is the number of bits to be transmitted and C is the channel capacity.
         C [b/s] is computed as C [b/s] = W * \log_2(1 + SNR)

        We leave computing SNR to the getSNR_dB method. As this varies depending on the channel type being used.

       */

        // Could this be implemented in a better manner? Absolutely, but right now getting the layer from the worker or
        // adding it to the SDNNodeProperties is the same. ; _ ;
        int srcLayer = ((Worker) src.getProtocol(Worker.getPid())).getLayer();
        int dstLayer = ((Worker) dest.getProtocol(Worker.getPid())).getLayer();
        SDNNodeProperties srcProps = (SDNNodeProperties) src.getProtocol(SDNNodeProperties.getPid());
        SDNNodeProperties dstProps = (SDNNodeProperties) dest.getProtocol(SDNNodeProperties.getPid());
        double msgSize = 1; // MBytes

        if (msg instanceof Message) { // Better solution is wrapping the events in a message wrapper class
            msgSize = ((Message) msg).getSize();
        }
        if (distance(srcProps, dstProps) == 0) {
            EDSimulator.add(1, msg, dest, pid);
            return;
        }

        SNRCalculator snrCalc = channelTypes.get(channelTypePerLayer.get(srcLayer).get(dstLayer));
        double T = msgSize * 8e6; // Convert from MBytes to bits
        double W = srcProps.getBANDWIDTH() * 1e6;
        double SNR_dB = snrCalc.getSNR_dB(srcProps, dstProps, T);
        double SNR_linear = Math.pow(10, SNR_dB / 10); // [linear]
        double C_2 = W * log2(1 + SNR_linear); // Channel Capacity [bit/s]

        long delay = Math.max(Math.round(T / C_2), 1);
        delay += (range == 1 ? min : min + CommonState.r.nextLong(range));
        EDSimulator.add(delay, msg, dest, pid);
    }



    public double log2(double n) {
        return Math.log(n) / Math.log(2);
    }

    /**
     * Returns a random
     * delay, that is drawn from the configured interval according to the uniform
     * distribution.
     */
    public long getLatency(Node src, Node dest) {
        return 0L;
    }

    public static double distance(SDNNodeProperties src, SDNNodeProperties dst) {
        double X_dist = (src.getX() - dst.getX());
        double Y_dist = (src.getY() - dst.getY());
        return Math.sqrt(X_dist * X_dist + Y_dist * Y_dist);
    }

    protected double getChannelGain(SDNNodeProperties src, SDNNodeProperties dst) {

        return src.getPATH_LOSS_CONSTANT() * Math.pow(this.distance(src, dst), -src.getPATH_LOSS_EXPONENT());
    }

    protected List<SNRCalculator>getChannelSNRCalculator(String classPaths, String prefix){
        List<SNRCalculator> classList = new ArrayList<>();
        String[] paths = classPaths.split(";");
        for (String path : paths) {
            try {
                // Load the class dynamically
                Class<?> cls = Class.forName(path);
                // Check if the class is a subclass of SNRCalculator
                if (SNRCalculator.class.isAssignableFrom(cls)) {
                    // Call the class constructor and cast the class to SNRCalculator
                    Class pars[] = {String.class};
                    Object objpars[] = {prefix};
                    Constructor cons = cls.getConstructor(pars);
                    SNRCalculator snrCalculator = (SNRCalculator) cons.newInstance(objpars);
                    classList.add(snrCalculator);
                } else {
                    System.out.println("Class " + path + " does not implement SNRCalculator. Please have the classes implement the interface.");
                }
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }
        return classList;
    }
}
