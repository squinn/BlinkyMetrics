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
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Example command line:
 * <p>
 * java -classpath out/production/Server BlinkyMetricsServer
 * <p>
 * Created by squinn on 4/21/2017.
 */
public class BlinkyMetricsServer {

    private static final int DEFAULT_PORT = 7272;

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
        }), "/metrics");

        server.start();
        server.join();

        System.out.println("BlinkyMetricsServer started, and accepting connections on port: " + DEFAULT_PORT);

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

    private Map<String, Metrics> metricsPerHost = new HashMap<>();

    private void updateHostMetrics(JSONObject jsonObject) throws JSONException {
        final String hostName = jsonObject.getString("hostName");

        Metrics metrics = metricsPerHost.get(hostName);
        if(metrics == null) {
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
