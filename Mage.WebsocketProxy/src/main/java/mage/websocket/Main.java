package mage.websocket;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private static final int DEFAULT_WS_PORT = 17280;
    private static final String DEFAULT_XMAGE_HOST = "localhost";
    private static final int DEFAULT_XMAGE_PORT = 17171;

    public static void main(String[] args) throws Exception {
        int wsPort = Integer.parseInt(System.getProperty("ws.port", String.valueOf(DEFAULT_WS_PORT)));
        String xmageHost = System.getProperty("xmage.host", DEFAULT_XMAGE_HOST);
        int xmagePort = Integer.parseInt(System.getProperty("xmage.port", String.valueOf(DEFAULT_XMAGE_PORT)));

        logger.info("Starting WebSocket proxy on port {}", wsPort);
        logger.info("Proxying to XMage server at {}:{}", xmageHost, xmagePort);

        Server server = new Server(wsPort);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);

        WebSocketServlet wsServlet = new WebSocketServlet() {
            @Override
            public void configure(WebSocketServletFactory factory) {
                factory.getPolicy().setMaxTextMessageSize(1024 * 1024);
                factory.setCreator((req, resp) -> new XmageWebSocketHandler(xmageHost, xmagePort));
            }
        };

        context.addServlet(new ServletHolder(wsServlet), "/ws");

        server.start();
        logger.info("WebSocket proxy started on ws://0.0.0.0:{}/ws", wsPort);
        server.join();
    }
}
