import org.hyperic.sigar.Cpu;
import org.hyperic.sigar.CpuPerc;
import org.hyperic.sigar.Sigar;
import org.hyperic.sigar.SigarException;

/**
 * Example command line:
 * <p>
 * java -classpath out/production/Agent;Agent/lib/sigar.jar -Djava.library.path=Agent/lib/sigar BlinkyMetricsAgent 192.168.5.100:8080
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

        int charsToErase = 0;
        while (true) {

            final double totalCpuUsagePercentage = getTotalCpuUsagePercentage(sigar);
            final CpuPerc[] cpuPercs = getCpuUsagePercentages(sigar);
            charsToErase = printCpuUsagePercentages(totalCpuUsagePercentage, cpuPercs, charsToErase);

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                // Purposefully empty
            }
        }
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

    private int printCpuUsagePercentages(double totalCpuUsagePercentage, CpuPerc[] cpuPercs, int charsToErase) {

        // Erase what we printed last time in the loop
        eraseChars(charsToErase);

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
    private void eraseChars(int charsToErase) {
        // Erase what we printed last time in the loop
        for (int i = 0; i < charsToErase + 1; i++) {
            System.out.print("\b");
        }
    }
}
