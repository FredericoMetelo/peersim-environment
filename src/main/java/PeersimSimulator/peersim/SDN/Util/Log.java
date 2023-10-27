package PeersimSimulator.peersim.SDN.Util;

import PeersimSimulator.peersim.core.CommonState;

public class Log {
    private static final int on = 0;

    public static final int OFF = -1;
    public static final int DEBUG = 0;
    public static final int INFO = 1;

    public static final boolean LOG_ERRORS = true;

    public static void log(String message, int level){
        if(on <= level)
            System.out.println(message);
    }
    public static void info(String message){
        if(on <= INFO)
            System.out.println(message);
    }
    public static void dbg(String message){
        if(on <= DEBUG)
            System.out.println("\033[0;33m" + message + "\033[0m");
    }
    public static void err(String message){
        if(LOG_ERRORS)
            System.err.println(message);
    }

    public static void logDbg(String protocol, int id, String event, String msg){
        String timestamp = String.format("|%04d| ", CommonState.getTime());
        String base = String.format("%s ( %03d )| ", protocol, id);

        Log.dbg(timestamp + base + String.format(" %-20s |", event) + " msg:" + msg);
    }
    public static void logInfo(String protocol, int id, String event, String msg){
        String timestamp = String.format("|%04d| ", CommonState.getTime());
        String base = String.format("%s ( %03d )| ", protocol, id);

        Log.info(timestamp + base + String.format(" %-20s |", event) + " info:" + msg);
    }
    public static void logErr(String protocol, int id, String event, String msg){
        String timestamp = String.format("|%04d| ", CommonState.getTime());
        String base = String.format("%s ( %03d )| ", protocol, id);

        Log.err(timestamp + base + String.format(" %-20s |", event) + " msg:" + msg);
    }
}
