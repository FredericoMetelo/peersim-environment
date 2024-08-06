package PeersimSimulator.peersim.env.Transport;

import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.core.Network;
import PeersimSimulator.peersim.core.Node;
import PeersimSimulator.peersim.edsim.EDSimulator;
import PeersimSimulator.peersim.env.Nodes.Events.Message;
import PeersimSimulator.peersim.env.Nodes.Workers.Worker;
import PeersimSimulator.peersim.transport.Transport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class GenericByteRateBandwidthTransport implements Transport {
    protected static final String PAR_NO_LAYERS= "NO_LAYERS";
    public static final String PAR_BandwidtBitRate = "BandwidtBitRateBetweenLayers";
    public List<List<Double>> bitRatePerLayer; //Mb per second
    public double noLayers;
    public GenericByteRateBandwidthTransport(String prefix) {
        noLayers = Configuration.getInt(prefix + "." + PAR_NO_LAYERS);
        String _bitRatePerLayer = Configuration.getString(prefix + "." + PAR_BandwidtBitRate, "100");
        String[] _bitRatePerLayerLists = _bitRatePerLayer.split(";");
        bitRatePerLayer = new ArrayList<>();
        for (String _bitRatePerLayerList : _bitRatePerLayerLists) {
           String[] _bitRatePerLayerListArray = _bitRatePerLayerList.split(",");
           List<Double> bitRatePerLayerList = Arrays.stream(_bitRatePerLayerListArray).map(Double::parseDouble).collect(Collectors.toList());
           bitRatePerLayer.add(bitRatePerLayerList);
        }
    }

    @Override
    public void send(Node src, Node dest, Object msg, int pid) {
        int srcLayer = ((Worker) src.getProtocol(Worker.getPid())).getLayer();
        int dstLayer = ((Worker) dest.getProtocol(Worker.getPid())).getLayer();
        long delay = (long) Math.floor(((Message) msg).getSize()/ bitRatePerLayer.get(srcLayer).get(dstLayer));
        EDSimulator.add(delay, msg, dest, pid);
    }

    @Override
    public long getLatency(Node src, Node dest) {
        int srcLayer = ((Worker) src.getProtocol(Worker.getPid())).getLayer();
        int dstLayer = ((Worker) dest.getProtocol(Worker.getPid())).getLayer();
        return (long) Math.floor(100/ bitRatePerLayer.get(srcLayer).get(dstLayer));
    }



    @Override
    public Object clone() {
        return this;
    }
}
