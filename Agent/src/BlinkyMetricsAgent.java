import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.hyperic.sigar.Cpu;
import org.hyperic.sigar.CpuPerc;
import org.hyperic.sigar.Sigar;
import org.hyperic.sigar.SigarException;
import org.json.JSONObject;

import java.net.InetAddress;

/**
 * Example command line:
 * <p>
 * java -classpath out/production/Agent;Agent/lib/* -Djava.library.path=Agent/lib/sigar BlinkyMetricsAgent 192.168.5.100:8080
 * <p>
 * Created by squinn on 4/21/2017.
 */
public class BlinkyMetricsAgent {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java BlinkyMetricsAgent <server[:port]>");
            return;
        }
        new BlinkyMetricsAgent(args[0]).start();
    }

    private String serverAddress;

    private BlinkyMetricsAgent(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    @SuppressWarnings("InfiniteLoopStatement")
    private void start() {

        final Sigar sigar;
        try {
            sigar = new Sigar();
            final Cpu[] cpus = sigar.getCpuList();
            System.out.println("\nStarting blinky metrics agent monitoring of " + cpus.length + " cpus/cores\n");
        } catch (SigarException e) {
            System.err.println("Fatal error, unable to initialize sigar API.");
            e.printStackTrace();
            return;
        }

        final String hostName = getHostName();

        boolean connected = false;
        while (true) {

            // Get the current metrics
            final double totalCpuUsagePercentage = getTotalCpuUsagePercentage(sigar);
            final CpuPerc[] cpuPercs = getCpuUsagePercentages(sigar);
            // printCpuUsagePercentages(totalCpuUsagePercentage, cpuPercs);

            // Convert metrics to JSON
            final JSONObject json = new JSONObject();
            json.put("hostName", hostName);
            json.put("cpuUsage", totalCpuUsagePercentage);

            // Post to the server (if available)
            if(postMetricsToServer(json)) {
                if(!connected) {
                    System.out.println("Successfully connected to: " + serverAddress);
                    connected = true;
                }
            } else {
                if(connected) {
                    System.out.println("Attempting to reconnect to: " + serverAddress);
                    connected = false;
                }
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                // Purposefully empty
            }
        }
    }

    private boolean postMetricsToServer(JSONObject json) {
        try {
            final CloseableHttpClient httpclient = HttpClients.createDefault();
            final HttpPost httpPost = new HttpPost("http://" + serverAddress + "/metrics");
            httpPost.setHeader("Content-type", "application/json");
            httpPost.setEntity(new StringEntity(json.toString()));
            httpclient.execute(httpPost);
        } catch (Throwable t) {
            return false;
        }
        return true;
    }

    private String getHostName() {
        String hostname = "Unknown";

        try {
            InetAddress addr = InetAddress.getLocalHost();
            hostname = addr.getHostName();
        } catch (Throwable t) {
            System.err.println("Hostname can not be resolved");
        }
        return hostname;
    }

    private CpuPerc[] getCpuUsagePercentages(Sigar sigar) {
        try {
            return sigar.getCpuPercList();
        } catch (SigarException e) {
            System.err.println("Unable to get CPU usage due to: " + e.getMessage());
            return new CpuPerc[0];
        }
    }

    private double getTotalCpuUsagePercentage(Sigar sigar) {
        try {
            return sigar.getCpuPerc().getCombined();
        } catch (SigarException e) {
            System.err.println("Unable to get CPU usage due to: " + e.getMessage());
            return 0.0;
        }
    }

    @SuppressWarnings("unused")
    private int printCpuUsagePercentages(double totalCpuUsagePercentage, CpuPerc[] cpuPercs) {
        int charsPrinted = 0;
        charsPrinted += printString("TOTAL: " + String.format("%3.0f%%", totalCpuUsagePercentage * 100.0) + ", ");
        for (int i = 0; i < cpuPercs.length; i++) {
            final CpuPerc cpuPerc = cpuPercs[i];
            final String formattedPercent = String.format("%3.0f%%", cpuPerc.getCombined() * 100.0);
            charsPrinted += printString("CPU " + (i + 1) + ": " + formattedPercent);
            if (i < cpuPercs.length - 1) {
                charsPrinted += printString(", ");
            }
        }
        return charsPrinted;
    }

    private int printString(String str) {
        System.out.print(str);
        return str.length();
    }


    @SuppressWarnings("unused")
    private void eraseChars(int charsToErase) {
        // Erase what we printed last time in the loop
        for (int i = 0; i < charsToErase + 1; i++) {
            System.out.print("\b");
        }
    }
}
