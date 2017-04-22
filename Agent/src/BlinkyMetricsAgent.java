/**
 * Usage: java BlinkyMetricsAgent <server IP address>
 *
 * Created by squinn on 4/21/2017.
 */
public class BlinkyMetricsAgent {
    public static void main(String[] args) {
        if(args.length < 1) {
            System.err.println("Usage: java BlinkyMetricsAgent <server IP address>");
            return;
        }
        System.out.println("Starting Blinky Agent...");
    }
}
