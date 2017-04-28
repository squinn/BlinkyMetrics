import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Example command line:
 * <p>
 * java -classpath out/production/Agent;Agent/lib/* BlinkyMetricsClient 192.168.5.100:8080
 * <p>
 * Created by squinn on 4/21/2017.
 */
public class BlinkyMetricsClient {

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
                    processPacket(packet);
                }
            } catch (Throwable t) {
                if(connected) {
                    System.out.println("Attempting to automatically reconnect to: " + serverAddress);
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

    private void processPacket(String packet) {
        final JSONObject jsonObject = new JSONObject(packet);
        final JSONArray hosts = jsonObject.getJSONArray("hosts");
        for(int i=0; i < hosts.length(); i++) {
            System.out.println("Host " + (i + 1) + " CPU: " + String.format("%3.0f%%", hosts.getJSONObject(i).getDouble("cpuUsage") * 100.0));
        }
    }

}
