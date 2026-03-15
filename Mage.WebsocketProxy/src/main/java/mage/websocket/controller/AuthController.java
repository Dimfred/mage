package mage.websocket.controller;

import com.google.gson.JsonObject;
import mage.remote.Connection;
import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

public class AuthController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final String xmageHost;
    private final int xmagePort;
    private final Supplier<mage.remote.Session> sessionSupplier;

    public AuthController(Session wsSession, String xmageHost, int xmagePort, Supplier<mage.remote.Session> sessionSupplier) {
        super(wsSession);
        this.xmageHost = xmageHost;
        this.xmagePort = xmagePort;
        this.sessionSupplier = sessionSupplier;
    }

    @Override
    public String getPrefix() {
        return "auth";
    }

    @Override
    public void handleMessage(String action, JsonObject payload) {
        switch (action) {
            case "login":
                handleLogin(payload);
                break;
            default:
                sendError("Unknown action: " + action);
                break;
        }
    }

    private void handleLogin(JsonObject payload) {
        String username = payload.get("username").getAsString();
        logger.info("Logging in as '{}'", username);

        mage.remote.Session xmageSession = sessionSupplier.get();

        Connection connection = new Connection();
        connection.setHost(xmageHost);
        connection.setPort(xmagePort);
        connection.setUsername(username);
        connection.setProxyType(Connection.ProxyType.NONE);

        boolean success = xmageSession.connectStart(connection);
        if (!success) {
            sendError(xmageSession.getLastError());
            return;
        }

        JsonObject result = new JsonObject();
        result.addProperty("sessionId", xmageSession.getSessionId());
        sendToWs("auth.response", result);
    }
}
