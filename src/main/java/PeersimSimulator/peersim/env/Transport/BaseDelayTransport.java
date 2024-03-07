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
    private static final long SPECTRAL_NOISE_POWER =  -174; // dBm/Hz

//---------------------------------------------------------------------
//Fields
//---------------------------------------------------------------------


    /**
     * String name of the parameter used to configure the minimum latency.
     * @config
     */
    private static final String PAR_MINDELAY = "mindelay";

    /**
     * String name of the parameter used to configure the maximum latency.
     * Defaults to {@value #PAR_MINDELAY}, which results in a constant delay.
     * @config
     */
    private static final String PAR_MAXDELAY = "maxdelay";

//---------------------------------------------------------------------
//Fields
//---------------------------------------------------------------------
    // ====== Start of original class by peersim =====
    /** Minimum delay for message sending */
    private final long min;

    /** Difference between the max and min delay plus one. That is, max delay is
     * min+range-1.
     */
    private final long range;
    // ====== End of original class by peersim ======
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

        min = Configuration.getLong(prefix + "." + PAR_MINDELAY);
        long max = Configuration.getLong(prefix + "." + PAR_MAXDELAY,min);
        if (max < min)
            throw new IllegalParameterException(prefix+"."+PAR_MAXDELAY,
                    "The maximum latency cannot be smaller than the minimum latency");
        range = max-min+1;
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
        //
        // Used the units as descirbed in: https://en.wikipedia.org/wiki/Channel_capacity
        // Found the expression for the channel gain in slide 11 of the ppt: https://personal.utdallas.edu/~torlak/courses/ee4367/lectures/lectureradio.pdf

        // If push comes to shove. We simply assume an SNR of 25 bottom of good but not perfect signal.

        // 360 we are using free space equation instead: https://www.sharetechnote.com/html/Handbook_LTE_Fading.html#Path_Loss_FreeSpace


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
        /*
        => Option One: C
         We wish to use the Shannon-Hartley theorem to calculate the delay. We will use the following formula:

         t^c = \frac{T}{C}

         Where T [b] is the number of bits to be transmitted and C is the channel capacity.
         C [b/s] is computed as C [b/s] = W * \log_2(1 + \frac{P_r}{N_0 W})

         Where W [Hz] is the bandwidth, P_r [dBm] is the received power, N_0 [dBm/Hz] is the noise power and W [Hz] is the bandwidth.
         P_r [dBm] is computed with the Free Space Equation, meaning (Note: P_t is [dBm] and d is [m]):
        (src: https://en.wikipedia.org/wiki/Free-space_path_loss ; https://en.wikipedia.org/wiki/Friis_transmission_equation)

         P_r [dB] = P_t [dB] + G_t [dB] + G_r [dB] + 20 log_{10} (\frac{\lambda}{4 \pi d})

         Values for the constants:
         lambda = \frac{c[m/s]}{f [GHz]} ; c = 3e8 ; f = 2.4e9;

         Gain of antenna's may vary. Will consider 6dBi - 9dBi for the gain of the antennas, as recommended by this
         randos on the internet:
         https://moonrakeronline.com/us/blog/what-is-antenna-gain-dbi-db
         https://www.antenna-theory.com/design/raspberry-pi-antenna.php // To add later, this dude measured gaion for Rbpi3

        => Option 2: C_2
        https://dsp.stackexchange.com/questions/89698/how-to-calculate-the-signal-to-noise-ratio-snr-in-db-different-units

         Note:
          d >> \lambda
          Inacurate because we assume no obstacles. We will use the Free Space Path Loss Equation.
       */
        double T = msgSize * 8e6; // Convert from MBytes to bits
        double W = srcProps.getBANDWIDTH() * 1e6; // Convert from Mhz to Hz
//        double gain_tx = 6; // dBi
//        double gain_rx = 6; // dBi
        double d = distance(srcProps, dstProps); // m
        double lambda = 3e8 / 2.4e9; // wavelength : c/f [m] ; c = 3e8 m/s ; f = 2.4e9 Hz;
        double P_t = srcProps.getTRANSMISSION_POWER(); // [dBm]

        double N_0 = SPECTRAL_NOISE_POWER + 10*Math.log10(W); // [dBm] convert from dBm/Hz to dBm
        double h = 10 * Math.log10(((lambda* lambda) / (16 * Math.PI * Math.PI))) - 20 * Math.log10(d); // [dB]

        double SNR_dB = P_t + h - N_0; // [dB]
        double SNR_linear = Math.pow(10, SNR_dB/10); // [linear]
        double C_2 = W * log2(1 + SNR_linear); // Channel Capacity [bit/s]

//        double P_r = P_t + gain_tx + gain_rx + 20 * log2(lambda/(4 * Math.PI * d));
//        double C = W * log2(1 + P_r / (N_0)); // Channel Capacity [bit/s]

        long delay = Math.max(Math.round( T / C_2), 1);
//        delay += (range==1?min:min + CommonState.r.nextLong(range));
        EDSimulator.add(delay, msg, dest, pid);
    }

    /*  Old Code
        double bandwidth = srcProps.getBANDWIDTH(); // * 1e+6; // Convert from Mhz to Hz
        double numberOfBytes = msgSize; // * 1e+6; // convert from MBytes to Bytes
        double transmissionPower = srcProps.getTRANSMISSION_POWER() ; // Math.pow(10, srcProps.getTRANSMISSION_POWER()/10); // convert dBm to Watts
        double spectralNoisePower = SPECTRAL_NOISE_POWER; // Math.pow(10, SPECTRAL_NOISE_POWER/10); // convert dBm/Hz to Watts/Hz

        double transmissionRate = bandwidth * Math.log10(1 +
                (getChannelGain(srcProps, dstProps) * transmissionPower)
                / (bandwidth * spectralNoisePower)) ;
     */

    public double log2(double n){
        return Math.log(n) / Math.log(2);
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

        return src.getPATH_LOSS_CONSTANT() * Math.pow(this.distance(src, dst), -src.getPATH_LOSS_EXPONENT());
    }
}


