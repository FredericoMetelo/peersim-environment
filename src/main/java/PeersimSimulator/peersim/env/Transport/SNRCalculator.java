package PeersimSimulator.peersim.env.Transport;

import PeersimSimulator.peersim.env.Links.SDNNodeProperties;

public interface SNRCalculator {

    /**
     * Returns the SNR in dB for the given the channel between the source and destination and their respective properties.
     * @param srcProps
     * @param dstProps
     * @param bandwidth_Hz
     * @return
     */

    double getSNR_dB(SDNNodeProperties srcProps, SDNNodeProperties dstProps, double bandwidth_Hz);
}
