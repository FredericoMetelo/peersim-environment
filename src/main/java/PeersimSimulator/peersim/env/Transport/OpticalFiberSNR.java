package PeersimSimulator.peersim.env.Transport;

import PeersimSimulator.peersim.env.Links.SDNNodeProperties;
import PeersimSimulator.peersim.config.*;

public class OpticalFiberSNR implements SNRCalculator{

    public static final String PAR_SNR = "SNR";
    public double SNR_dB;
    public OpticalFiberSNR(String prefix) {
        SNR_dB = Configuration.getDouble(prefix + "." + PAR_SNR, 45);
    }
    public double getSNR_dB(SDNNodeProperties srcProps, SDNNodeProperties dstProps, double bandwidth_Hz) {
        /*
         * In my understanding, a possible way Optical Fiber systems are designed is that by specifying BER (Bit Error Rate)
         * building the system keeping track of a Noise Budget (Each component on the system will add some noise reducing
         * signal strenght) and then picking a transmission power that will allow the system to reach the desired BER.
         * See [0] for more information on this methodology.
         *
         * References:
         * [0] http://www.svphotonics.com/pub/pub029.pdf
         * [1] http://opticalcloudinfra.com/index.php/2017/07/09/shannon-limit-sets-upper-bar-optical-networks/
         * [n] https://www.youtube.com/watch?v=UOLRP52oOPI  (Video on Converting BER to SNR, pretty good explanation)
         */
        return SNR_dB; // TODO make this user definable.
    }
}
