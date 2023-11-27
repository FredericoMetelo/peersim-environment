package PeersimSimulator.peersim.env.Transport;

import PeersimSimulator.peersim.env.Links.SDNNodeProperties;
import PeersimSimulator.peersim.env.Nodes.Events.Message;
import PeersimSimulator.peersim.config.*;
import PeersimSimulator.peersim.core.*;
import PeersimSimulator.peersim.edsim.*;
import PeersimSimulator.peersim.transport.Transport;


/**
 * Implement a transport layer that reliably delivers messages with a random
 * delay, that is drawn from the configured interval according to the uniform
 * distribution.
 *
 * @author Alberto Montresor
 * @version $Revision: 1.14 $
 */
public final class BaseDelayTransport implements Transport
{

//---------------------------------------------------------------------
//Parameters
//---------------------------------------------------------------------

    /**
     * String name of the parameter used to configure the minimum latency.
     * @config
     */
    private static final long SPECTRAL_NOISE_POWER =  174; // dBm/Hz

//---------------------------------------------------------------------
//Fields
//---------------------------------------------------------------------
    private static final String PAR_NAME = "name";

    private static final String PROPERTY_PROTOCOL = "protocol.props";

    private static int pid;

    private final double BANDWIDTH;
    private static final String PAR_BANDWIDTH = "B";

    private final double PATH_LOSS_CONSTANT;
    private static final String PAR_PATH_LOSS_CONSTANT = "Beta1";

    private final double PATH_LOSS_EXPONENT;
    private static final String PAR_PATH_LOSS_EXPONENT = "Beta2";

    private final double TRANSMISSION_POWER;
    private static final String PAR_TRANSMISSION_POWER = "P_ti";

//---------------------------------------------------------------------
//Initialization
//---------------------------------------------------------------------

    /**
     * Reads configuration parameter.
     */
    public BaseDelayTransport(String prefix)
    {

        // TODO Add support for having multiple, may need to not configure on startup and only configure on the call
        //  to the send method, as on runtime we may not know this values whe we add multiple layers.

        // TODO Change the parameter to have a general name and not fetch the properties of the Node Properties.
        BANDWIDTH = Configuration.getDouble( PROPERTY_PROTOCOL + "." + PAR_BANDWIDTH, 2); // 2Mhz
        PATH_LOSS_CONSTANT = Configuration.getDouble( PROPERTY_PROTOCOL + "." + PAR_PATH_LOSS_CONSTANT, 0.001);
        PATH_LOSS_EXPONENT = Configuration.getDouble( PROPERTY_PROTOCOL + "." + PAR_PATH_LOSS_EXPONENT, 4);
        TRANSMISSION_POWER = Configuration.getDouble( PROPERTY_PROTOCOL + "." + PAR_TRANSMISSION_POWER, 20);// 20 dbm
    }

//---------------------------------------------------------------------

    /**
     * Returns <code>this</code>. This way only one instance exists in the system
     * that is linked from all the nodes. This is because this protocol has no
     * node specific state.
     */
    public Object clone()
    {
        return this;
    }

//---------------------------------------------------------------------
//Methods
//---------------------------------------------------------------------

    /**
     * Delivers the message with a delay based on Shannon-Hartley theorem
     */
    public void send(Node src, Node dest, Object msg, int pid)
    {
        //
        // avoid calling nextLong if possible
        // I will base myself on the expression proposed by the authors of the Fog Paper for the delay.
        // t^c = \frac{2 * T * processingPower^o}{r_{l,o}}
        // $r_{l,o} = B*\log(1 + \frac{g_{i,j}P_{t_x, i}}{BN_0})$
        SDNNodeProperties srcProps = (SDNNodeProperties) src.getProtocol(SDNNodeProperties.getPid());
        SDNNodeProperties dstProps = (SDNNodeProperties) dest.getProtocol(SDNNodeProperties.getPid());
        double msgSize = 1; // MBytes

        if(msg instanceof Message){ // Better solution is wrapping the events in a message wrapper class
            msgSize = ((Message) msg).getSize();
        }
        if (distance(srcProps, dstProps) == 0){
            EDSimulator.add(1, msg, dest, pid);
            return;
        }
        double numberOfBytes = 2 * msgSize;
        double transmissionRate = srcProps.getBANDWIDTH() * Math.log(1 +
                (getChannelGain(srcProps, dstProps) * srcProps.getTRANSMISSION_POWER())
                / (srcProps.getBANDWIDTH() * SPECTRAL_NOISE_POWER)) ;
        long delay = Math.round( numberOfBytes / transmissionRate);
        EDSimulator.add(delay, msg, dest, pid);
    }

    /**
     * Returns a random
     * delay, that is drawn from the configured interval according to the uniform
     * distribution.
     */
    public long getLatency(Node src, Node dest)
    {
        return 0L;
    }

    private double distance(SDNNodeProperties src, SDNNodeProperties dst){
        double X_dist = (src.getX() - dst.getX());
        double Y_dist = (src.getY() - dst.getY());
        return Math.sqrt(X_dist*X_dist + Y_dist*Y_dist);
    }

    private double getChannelGain(SDNNodeProperties src, SDNNodeProperties dst){

        return src.getPATH_LOSS_CONSTANT() * Math.pow(this.distance(src, dst), src.getPATH_LOSS_EXPONENT());
    }
}


