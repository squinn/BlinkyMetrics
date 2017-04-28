import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.json.JSONException;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.*;

/**
 * Example command line:
 * <p>
 * java -classpath out/production/Server BlinkyMetricsServer
 * <p>
 * Created by squinn on 4/21/2017.
 */
public class BlinkyMetricsServer {

    private static final int DEFAULT_PORT = 7272;
    private static final long HOST_PRUNING_DELAY_MILLIS = 5000;         // How often will we prune inactive agents/hosts
    private static final long METRIC_UPDATE_DELAY_MILLIS = 500;         // How often will we send new metrics to the clients

    public static void main(String[] args) {
        try {
            new BlinkyMetricsServer().start();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private BlinkyMetricsServer() {
    }

    private void start() throws Throwable {
        Server server = new Server(DEFAULT_PORT);

        // Home page
        ServletHandler servletHandler = new ServletHandler();
        server.setHandler(servletHandler);
        servletHandler.addServletWithMapping(new ServletHolder(new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
                response.setContentType("text/html");
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().println("<h1>Blinky Metrics Server: Online</h1>");
                printHostSummary(response.getWriter());
            }
        }), "/");

        // Post or retrieve
        servletHandler.addServletWithMapping(new ServletHolder(new HttpServlet() {
            @Override
            protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

                try {
                    JSONObject jsonObject = parseMetricsJSON(request);
                    updateHostMetrics(jsonObject);
                    response.setContentType("text/html");
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.getWriter().println("OK");
                } catch (Throwable t) {

                    response.setContentType("text/html");
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.getWriter().println("Bad request: " + t.getMessage());

                    System.err.println("Received bad request: " + t.getMessage());
                }
            }

            @Override
            protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
                startClientConnection(request, response);
            }

        }), "/metrics");

        server.start();

        // Start a thread that will periodically prune the host list (to handle when hosts disconnect)
        final Timer uploadCheckerTimer = new Timer(true);
        uploadCheckerTimer.scheduleAtFixedRate(
            new TimerTask() {
                public void run() {
                    final long currentTimeMillis = System.currentTimeMillis();
                    final List<String> hostNamesToPurge = new ArrayList<>();
                    for (Map.Entry<String, Metrics> entry : metricsPerHost.entrySet()) {
                        if (currentTimeMillis - entry.getValue().lastUpdatedMillis > HOST_PRUNING_DELAY_MILLIS) {
                            hostNamesToPurge.add(entry.getKey());
                        }
                    }
                    for (String hostName : hostNamesToPurge) {
                        System.out.println("Removing inactive host: " + hostName);
                        metricsPerHost.remove(hostName);
                    }

                }
            }, 0, 2 * 1000
        );

        System.out.println("BlinkyMetricsServer started, and accepting connections on port: " + DEFAULT_PORT);
        server.join();

    }

    private void startClientConnection(final HttpServletRequest request, final HttpServletResponse response) {

        System.out.println("Client connected: " + request.getRemoteHost());
        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);

        final ScheduledFuture scheduledFuture = scheduledExecutorService.scheduleAtFixedRate(
            new Runnable() {
                @Override
                public void run() {
                    try {
                        response.getWriter().println("Boo");
                        response.flushBuffer();
                    } catch (IOException e) {
                        // Client probably disconnected, so shut down the thread
                        throw new RuntimeException(e);
                    }
                }
            },
            0,
            METRIC_UPDATE_DELAY_MILLIS,
            TimeUnit.MILLISECONDS
        );

        while (true) {
            if (scheduledFuture.isCancelled() || scheduledFuture.isDone()) {
                System.out.println("Client disconnected: " + request.getRemoteHost());
                return;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // Purposefully empty
            }
        }
    }

    private void printHostSummary(PrintWriter writer) {

        writer.println("<p>Registered Hosts: " + metricsPerHost.size() + "</p>");
        writer.println("<ol>");

        for (Map.Entry<String, Metrics> entry : metricsPerHost.entrySet()) {
            writer.println("<li>");
            writer.println("<b>" + entry.getKey() + "</b>");
            writer.println(" (Last Update: " + new Date(entry.getValue().lastUpdatedMillis) + ")");
            writer.println("</li>");
        }

        writer.println("</ol>");
    }

    // Purposefully synchronized collection
    private Map<String, Metrics> metricsPerHost = new ConcurrentHashMap<>();

    private void updateHostMetrics(JSONObject jsonObject) throws JSONException {
        final String hostName = jsonObject.getString("hostName");

        Metrics metrics = metricsPerHost.get(hostName);
        if (metrics == null) {
            System.out.println("Registering new host: " + hostName);
            metrics = new Metrics();
            metricsPerHost.put(hostName, metrics);
        }
        metrics.lastUpdatedMillis = System.currentTimeMillis();

    }

    private JSONObject parseMetricsJSON(HttpServletRequest request) throws IOException {
        final StringBuilder sb = new StringBuilder();
        String line;
        BufferedReader reader = request.getReader();
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        return new JSONObject(sb.toString());
    }

    private class Metrics {
        long lastUpdatedMillis;
    }

}
