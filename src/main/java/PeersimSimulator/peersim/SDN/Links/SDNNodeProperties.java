package PeersimSimulator.peersim.SDN.Links;

import PeersimSimulator.peersim.core.Protocol;

public class SDNNodeProperties implements Protocol {

    // ------------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------------
    /** 2d coordinates components. */
    private double x, y;

    private double bandwidth;
    private double pathLossConstant;
    private double pathLossExponent;
    // ------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------


    public SDNNodeProperties(String prefix) {
        /* Un-initialized coordinates defaults to -1. */
        x = y = -1;
        bandwidth = 2e6; // 2Mhz
        pathLossConstant = 1e-3;
        pathLossExponent = 4;
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

}