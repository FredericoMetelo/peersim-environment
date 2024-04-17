package PeersimSimulator.peersim.env.Transport;

import PeersimSimulator.peersim.env.Links.SDNNodeProperties;
import PeersimSimulator.peersim.config.*;


/**
 * Implement a transport layer that reliably delivers messages with a random
 * delay, that is drawn from the configured interval according to the uniform
 * distribution.
 *
 * @author Alberto Montresor
 * @version $Revision: 1.14 $
 */
public final class WirelessSNR implements SNRCalculator{

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


    //---------------------------------------------------------------------
//Fields
//---------------------------------------------------------------------
    // ====== Start of original class by peersim =====

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
    public WirelessSNR(String prefix)
    {

        PATH_LOSS_CONSTANT = Configuration.getDouble( prefix + "." + PAR_PATH_LOSS_CONSTANT, 0.001);
        PATH_LOSS_EXPONENT = Configuration.getDouble( prefix + "." + PAR_PATH_LOSS_EXPONENT, 4);
        TRANSMISSION_POWER = Configuration.getDouble( prefix + "." + PAR_TRANSMISSION_POWER, 20);// 20 dbm

    }

//---------------------------------------------------------------------

    //---------------------------------------------------------------------
//Methods
//---------------------------------------------------------------------
    public double getSNR_dB(SDNNodeProperties srcProps, SDNNodeProperties dstProps, double bandwidth_Hz){
        /* We consider the formulation in [0] to calculate the SNR in dBw.
         * \frac{P_r}{N_0 W}
         * s.t. P_r [dB] = P_t [dB] + G_t [dB] + G_r [dB] + 20 log_{10} (\frac{\lambda}{4 \pi d})
         *
         * Where W [Hz] is the bandwidth, P_r [dBm] is the received power, N_0 [dBm/Hz] is the noise power and W [Hz] is the bandwidth.
         * P_r [dBm] is computed with the Free Space Equation, meaning (Note: P_t is [dBm] and d is [m]).
         *
         * Values for the constants:
         * lambda = \frac{c[m/s]}{f [GHz]} ; c = 3e8 ; f = 2.4e9;
         *
         * Gain of antenna's may vary. Will consider 6dBi - 9dBi for the gain of the antennas, as recommended by this
         * randos on the internet [1,2].
         * Resoureces:
         * [0] https://dsp.stackexchange.com/questions/89698/how-to-calculate-the-signal-to-noise-ratio-snr-in-db-different-units
         * [1] https://moonrakeronline.com/us/blog/what-is-antenna-gain-dbi-db
         * [2] https://www.antenna-theory.com/design/raspberry-pi-antenna.php // To add later, this dude measured antenna gain for Rbpi3
         * [3] https://en.wikipedia.org/wiki/Free-space_path_loss ; https://en.wikipedia.org/wiki/Friis_transmission_equation
         *
         * Note:
         * d >> \lambda
         * Inacurate because we assume no obstacles. We will use the Free Space Path Loss Equation.
         */
         // Convert from Mhz to Hz
        double d = MaxCapacityTransport.distance(srcProps, dstProps); // m
        double lambda = 3e8 / 2.4e9; // wavelength : c/f [m] ; c = 3e8 m/s ; f = 2.4e9 Hz;
        double P_t = srcProps.getTRANSMISSION_POWER(); // [dBm]

        double N_0 = SPECTRAL_NOISE_POWER + 10*Math.log10(bandwidth_Hz); // [dBm] convert from dBm/Hz to dBm
        double h = 10 * Math.log10(((lambda* lambda) / (16 * Math.PI * Math.PI))) - 20 * Math.log10(d); // [dB]

        double SNR_dB = P_t + h - N_0; // [dB]
        return SNR_dB;
    }

}


