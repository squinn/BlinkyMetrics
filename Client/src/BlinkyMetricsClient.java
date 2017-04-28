import jssc.SerialPortList;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.List;

/**
 * Example command line:
 * <p>
 * java -classpath out/production/Agent;Agent/lib/* BlinkyMetricsClient 192.168.5.100:8080
 * <p>
 * Created by squinn on 4/21/2017.
 */
public class BlinkyMetricsClient {

    private static final int LED_FRAME_RATE_DELAY_MILLIS = 250;             // Number of milliseconds between LED frame updates
    private static final int STATUS_INDICATOR_LIGNT_DELAY_MILLIS = 750;     // Number of seconds between flashes of the status indicator light

    private static final int[] VALID_LIGHT_INDEXES = new int[] {0,1,2,4,5,6,7,9,10,11,12,13};   // Which lights really work on our output device

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java BlinkyMetricsClient <server[:port]>");
            return;
        }
        new BlinkyMetricsClient(args[0]).start();
    }

    private String serverAddress;

    private BlinkyMetricsClient(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    @SuppressWarnings("InfiniteLoopStatement")
    private void start() {

        final LedManager ledManager = new LedManager();
        ledManager.start();

        System.out.println("BlinkinMetricsClient started, attempting to connect to: " + serverAddress);

        // Continuously wait on available connections to our server
        boolean connected = false;
        while (true) {
            try {
                final CloseableHttpClient httpclient = HttpClients.createDefault();
                final HttpGet httpGet = new HttpGet("http://" + serverAddress + "/metrics");
                final CloseableHttpResponse response = httpclient.execute(httpGet);
                final BufferedReader inputReader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));

                System.out.println("Successfully connected to: " + serverAddress);
                connected = true;
                String packet;
                while ((packet = inputReader.readLine()) != null) {
                    processPacket(packet, ledManager);
                }
            } catch (Throwable t) {
                if (connected) {
                    System.out.println("Attempting to automatically reconnect to " + serverAddress + " due to: " + t.getMessage());
                }
                connected = false;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // Purposefully emtpy
                }
            }
        }
    }

    private int hostCount = 0;

    private void processPacket(String packet, LedManager ledManager) {
        final JSONObject jsonObject = new JSONObject(packet);
        final JSONArray hosts = jsonObject.getJSONArray("hosts");

        if (hosts.length() != hostCount) {
            hostCount = hosts.length();
            System.out.println("Receiving data for " + hostCount + " hosts");
        }

        final Map<String, Metrics> metricsPerHost = new HashMap<>();
        for (int i = 0; i < hosts.length(); i++) {
            final JSONObject hostJsonObject = hosts.getJSONObject(i);
            // System.out.println("Host " + (i + 1) + " CPU: " + String.format("%3.0f%%", hostJsonObject.getDouble("cpuUsage") * 100.0));
            final Metrics metrics = new Metrics();
            metrics.cpuUsage = hostJsonObject.getDouble("cpuUsage");
            metricsPerHost.put(hostJsonObject.getString("hostName"), metrics);
        }

        ledManager.updateLeds(metricsPerHost);
    }

    private class Metrics {
        double cpuUsage;
    }

    private class LedManager {

        private BlinkyTapeController blinkyTapeController = null;

        private boolean checkBlinkyConnection() {
            if (blinkyTapeController == null) {
                final String blinkyPort = getBlinkyPort();
                if (blinkyPort == null) {
                    return false;
                }
                try {
                    blinkyTapeController = new SerialBlinkyTapeController(blinkyPort);
                } catch (Throwable t) {
                    System.err.println("Failure to connect to blinky on port " + blinkyPort + " due to: " + t.getMessage());
                    return false;
                }
            }
            return true;
        }

        private long lastMetricsUpdatedMillis = 0;



        void updateLeds(Map<String, Metrics> metricsPerHost) {
            try {
                lastMetricsUpdatedMillis = System.currentTimeMillis();

                final List<Color> newLedColors = new ArrayList<>();
                for (Map.Entry<String, Metrics> entry : metricsPerHost.entrySet()) {
                    final double cpuUsage = entry.getValue().cpuUsage;
                    newLedColors.add(new Color((float)cpuUsage, 1.0f - (float)cpuUsage, 0.0f));
                }
                currentLedColors = newLedColors;

            } catch (Throwable t) {
                System.err.println("Unable to update Blinky leds: " + t.getMessage());
                t.printStackTrace();
            }
        }


        private long statusIndicatorOnMillis = 0;
        private boolean connected = false;
        private List<Color> currentLedColors = new ArrayList<>();

        private void drawCurrentFrame() {
            if (checkBlinkyConnection() && blinkyTapeController != null) {
                try {

                    final BlinkyFrameBuilder blinkyFrameBuilder = new BlinkyFrameBuilder()
                            .withAllLightsSetTo(Color.BLACK);

                    final long currentMillis = System.currentTimeMillis();
                    final List<Color> ledColors = currentLedColors;

                    // If no hosts are reporting in, then just blink a light to show if we're connected to the server or not
                    if(currentMillis - lastMetricsUpdatedMillis > 2000) {
                        ledColors.clear();
                    }
                    if(ledColors.size() <= 0) {
                        if (currentMillis - statusIndicatorOnMillis > STATUS_INDICATOR_LIGNT_DELAY_MILLIS) {
                            if (currentMillis - lastMetricsUpdatedMillis > 1000) {
                                blinkyFrameBuilder
                                        .withSpecificLightSetTo(0, Color.RED);
                            } else {
                                blinkyFrameBuilder
                                        .withSpecificLightSetTo(0, Color.GREEN);
                            }
                            statusIndicatorOnMillis = currentMillis;
                        }
                    } else {
                        // The host metric information is current, so render their CPU levels on the LEDs
                        int i = 0;
                        for (Color ledColor : ledColors) {
                            if(i >= VALID_LIGHT_INDEXES.length) {
                                break;
                            }
                            blinkyFrameBuilder.withSpecificLightSetTo(VALID_LIGHT_INDEXES[i], ledColor);
                            i++;
                        }
                    }

                    blinkyTapeController.renderFrame(blinkyFrameBuilder.build());

                    /*
                    blinkyTapeController.renderFrame(new BlinkyFrameBuilder()
                            .withAllLightsSetTo(Color.BLACK)
                            .withSpecificLightSetTo(0, Color.RED)
                            .withSpecificLightSetTo(1, Color.ORANGE)
                            .withSpecificLightSetTo(2, Color.YELLOW)
                            .withSpecificLightSetTo(3, Color.GREEN)
                            .withSpecificLightSetTo(4, Color.BLUE)
                            .withSpecificLightSetTo(5, Color.MAGENTA)
                            .withSpecificLightSetTo(6, Color.PINK)
                            .withSpecificLightSetTo(7, Color.RED)
                            .withSpecificLightSetTo(8, Color.ORANGE)
                            .withSpecificLightSetTo(9, Color.YELLOW)
                            .withSpecificLightSetTo(10, Color.GREEN)
                            .withSpecificLightSetTo(11, Color.BLUE)
                            .withSpecificLightSetTo(12, Color.MAGENTA)
                            .withSpecificLightSetTo(13, Color.PINK).build()
                    );
                    */

                    if (!connected) {
                        System.out.println("Found connection to Blinky device");
                        connected = true;
                    }

                } catch (Throwable t) {

                    if (connected) {
                        System.out.println("Blinky device appears to have disconnected");
                        connected = true;
                    }

                    if (blinkyTapeController != null) {
                        blinkyTapeController.close();
                    }
                    blinkyTapeController = null;
                }
            }
        }

        private String getBlinkyPort() {
            String[] portNames = SerialPortList.getPortNames();
            for (String portName : portNames) {
                if (portName.contains("COM3")) {
                    return portName;
                }
            }
            return null;
        }

        void start() {
            final Timer uploadCheckerTimer = new Timer(true);
            uploadCheckerTimer.scheduleAtFixedRate(
                    new TimerTask() {
                        public void run() {
                            drawCurrentFrame();
                        }
                    }, 0, LED_FRAME_RATE_DELAY_MILLIS
            );

        }

    }

}
