package PeersimSimulator.peersim.env.Links;

import PeersimSimulator.peersim.config.Configuration;
import PeersimSimulator.peersim.core.Protocol;
import PeersimSimulator.peersim.env.Records.Coordinates;

public class SDNNodeProperties implements Protocol {

    // ------------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------------
    /** 2d coordinates components. */
    private double x, y;

    private static final String PAR_NAME = "name";

    private static int pid;

    private final double BANDWIDTH;
    private static final String PAR_BANDWIDTH = "B";

    private final double PATH_LOSS_CONSTANT;
    private static final String PAR_PATH_LOSS_CONSTANT = "Beta1";

    private final double PATH_LOSS_EXPONENT;
    private static final String PAR_PATH_LOSS_EXPONENT = "Beta2";

    private final double TRANSMISSION_POWER;
    private static final String PAR_TRANSMISSION_POWER = "P_ti";

    // ------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------


    public SDNNodeProperties(String prefix) {
        /* Un-initialized coordinates defaults to -1. */
        pid = Configuration.getPid(prefix+ "."+PAR_NAME);
        x = y = -1; // meters
        BANDWIDTH = Configuration.getDouble( prefix + "." + PAR_BANDWIDTH, 2); // 2Mhz
        PATH_LOSS_CONSTANT = Configuration.getDouble( prefix + "." + PAR_PATH_LOSS_CONSTANT, 0.001);
        PATH_LOSS_EXPONENT = Configuration.getDouble( prefix + "." + PAR_PATH_LOSS_EXPONENT, 4);
        TRANSMISSION_POWER = Configuration.getDouble( prefix + "." + PAR_TRANSMISSION_POWER, 20);// 20 dbm

    }

    public Object clone() {
        SDNNodeProperties inp = null;
        try {
            inp = (SDNNodeProperties) super.clone();
        } catch (CloneNotSupportedException e) {
        } // never happens
        return inp;
    }

    public double getBANDWIDTH() {
        return BANDWIDTH;
    }


    public double getPATH_LOSS_CONSTANT() {
        return PATH_LOSS_CONSTANT;
    }


    public double getPATH_LOSS_EXPONENT() {
        return PATH_LOSS_EXPONENT;
    }


    public double getTRANSMISSION_POWER() {
        return TRANSMISSION_POWER;
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

    public Coordinates getCoordinates(){
        return new Coordinates(this.getX(), this.getY());
    }

    /**
     * Returns distance between this node and the coordinates given. IF said coordinates are null, returns MAX_VALUE.
     * @param other
     * @return distance between this node and the coordinates given. IF said coordinates are null, returns MAX_VALUE.
     */
    public double distanceTo(Coordinates other){
        if(other == null || this.getCoordinates() == null) return Double.MAX_VALUE;
        return Math.sqrt(Math.pow(this.getX() - other.X(), 2) + Math.pow(this.getY() - other.Y(), 2));
    }
}