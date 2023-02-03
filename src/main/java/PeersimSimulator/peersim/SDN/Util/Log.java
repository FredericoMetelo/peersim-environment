package PeersimSimulator.peersim.SDN.Links;

public class Log {
    private static final int on = 0;

    public static final int OFF = -1;
    public static final int DEBUG = 0;
    public static final int INFO = 1;

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
            System.out.println(message);
    }
}
