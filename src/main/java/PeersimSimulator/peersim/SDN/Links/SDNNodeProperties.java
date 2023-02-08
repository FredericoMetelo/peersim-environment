package PeersimSimulator.peersim.SDN.Links;

import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.core.Protocol;

public class SDNNodeProperties implements Protocol {

    // ------------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------------
    /** 2d coordinates components. */
    private double x, y;

    private static final String PAR_NAME = "name";

    private static int pid;

    private double bandwidth;
    private double pathLossConstant;
    private double pathLossExponent;

    private double transmission_power;
    // ------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------


    public SDNNodeProperties(String prefix) {
        /* Un-initialized coordinates defaults to -1. */
        pid = Configuration.getPid(prefix+ "."+PAR_NAME);
        x = y = -1; // meters
        bandwidth = 2; // 2Mhz
        pathLossConstant = 0.001;
        pathLossExponent = 4;
        transmission_power = 20;// 20 dbm

    }

    public Object clone() {
        SDNNodeProperties inp = null;
        try {
            inp = (SDNNodeProperties) super.clone();
        } catch (CloneNotSupportedException e) {
        } // never happens
        return inp;
    }

    public double getBandwidth() {
        return bandwidth;
    }

    public void setBandwidth(double bandwidth) {
        this.bandwidth = bandwidth;
    }

    public double getPathLossConstant() {
        return pathLossConstant;
    }

    public void setPathLossConstant(double pathLossConstant) {
        this.pathLossConstant = pathLossConstant;
    }

    public double getPathLossExponent() {
        return pathLossExponent;
    }

    public void setPathLossExponent(double pathLossExponent) {
        this.pathLossExponent = pathLossExponent;
    }

    public double getTransmission_power() {
        return transmission_power;
    }

    public void setTransmission_power(double transmission_power) {
        this.transmission_power = transmission_power;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public static int getPid() {
        return pid;
    }
}