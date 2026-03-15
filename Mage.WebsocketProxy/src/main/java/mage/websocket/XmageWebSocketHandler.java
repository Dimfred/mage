package mage.websocket;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mage.interfaces.MageClient;
import mage.interfaces.callback.ClientCallback;
import mage.remote.Session;
import mage.remote.SessionImpl;
import mage.utils.MageVersion;
import mage.websocket.controller.AuthController;
import mage.websocket.controller.BaseController;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class XmageWebSocketHandler extends WebSocketAdapter implements MageClient {

    private static final Logger logger = LoggerFactory.getLogger(XmageWebSocketHandler.class);
    private static final Gson gson = new Gson();

    private final String xmageHost;
    private final int xmagePort;
    private final Map<String, BaseController> controllers = new HashMap<>();
    private Session xmageSession;

    public XmageWebSocketHandler(String xmageHost, int xmagePort) {
        this.xmageHost = xmageHost;
        this.xmagePort = xmagePort;
    }

    @Override
    public void onWebSocketConnect(org.eclipse.jetty.websocket.api.Session session) {
        super.onWebSocketConnect(session);
        logger.info("WebSocket client connected: {}", session.getRemote());

        controllers.put("auth", new AuthController(session, xmageHost, xmagePort, this::getOrCreateXmageSession));
    }

    @Override
    public void onWebSocketText(String message) {
        logger.debug("Received: {}", message);

        JsonObject msg = JsonParser.parseString(message).getAsJsonObject();
        String type = msg.get("type").getAsString();
        JsonObject payload = msg.has("payload") ? msg.getAsJsonObject("payload") : new JsonObject();

        // type format: "prefix.action" e.g. "auth.login"
        int dot = type.indexOf('.');
        if (dot == -1) {
            sendError("Invalid message type, expected 'prefix.action': " + type);
            return;
        }

        String prefix = type.substring(0, dot);
        String action = type.substring(dot + 1);

        BaseController controller = controllers.get(prefix);
        if (controller == null) {
            sendError("Unknown controller: " + prefix);
            return;
        }

        controller.handleMessage(action, payload);
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        super.onWebSocketClose(statusCode, reason);
        logger.info("WebSocket client disconnected: {} - {}", statusCode, reason);
        cleanup();
    }

    @Override
    public void onWebSocketError(Throwable cause) {
        logger.error("WebSocket error", cause);
        cleanup();
    }

    private Session getOrCreateXmageSession() {
        if (xmageSession == null) {
            xmageSession = new SessionImpl(this);
        }
        return xmageSession;
    }

    private void cleanup() {
        if (xmageSession != null && xmageSession.isConnected()) {
            xmageSession.connectStop(false, false);
        }
        xmageSession = null;
        controllers.clear();
    }

    private void sendToWs(String type, JsonObject payload) {
        org.eclipse.jetty.websocket.api.Session session = getSession();
        if (session == null || !session.isOpen()) return;

        JsonObject msg = new JsonObject();
        msg.addProperty("type", type);
        if (payload != null) {
            msg.add("payload", payload);
        }
        try {
            session.getRemote().sendString(msg.toString());
        } catch (IOException e) {
            logger.error("Failed to send to WebSocket", e);
        }
    }

    private void sendError(String message) {
        JsonObject payload = new JsonObject();
        payload.addProperty("error", message);
        sendToWs("error", payload);
    }

    // -- MageClient interface --

    @Override
    public MageVersion getVersion() {
        return new MageVersion(XmageWebSocketHandler.class);
    }

    @Override
    public void connected(String message) {
        logger.info("XMage connected: {}", message);
    }

    @Override
    public void disconnected(boolean askToReconnect, boolean keepMySessionActive) {
        logger.info("XMage disconnected (askReconnect={}, keepSession={})", askToReconnect, keepMySessionActive);
        JsonObject payload = new JsonObject();
        payload.addProperty("askToReconnect", askToReconnect);
        payload.addProperty("keepMySessionActive", keepMySessionActive);
        sendToWs("xmage.disconnected", payload);
    }

    @Override
    public void showMessage(String message) {
        logger.info("Server message: {}", message);
        JsonObject payload = new JsonObject();
        payload.addProperty("message", message);
        sendToWs("server.message", payload);
    }

    @Override
    public void showError(String message) {
        logger.error("Server error: {}", message);
        JsonObject payload = new JsonObject();
        payload.addProperty("error", message);
        sendToWs("server.error", payload);
    }

    @Override
    public void onNewConnection() {
        logger.debug("New XMage connection established");
    }

    @Override
    public void onCallback(ClientCallback callback) {
        callback.decompressData();
        String method = callback.getMethod().toString();
        logger.debug("Callback: {} (id={})", method, callback.getMessageId());

        JsonObject msg = new JsonObject();
        msg.addProperty("type", "callback." + method.toLowerCase());
        msg.addProperty("messageId", callback.getMessageId());
        if (callback.getObjectId() != null) {
            msg.addProperty("objectId", callback.getObjectId().toString());
        }

        Object data = callback.getData();
        if (data != null) {
            try {
                msg.add("data", gson.toJsonTree(data));
            } catch (Exception e) {
                logger.warn("Could not serialize callback data for {}: {}", method, e.getMessage());
                msg.addProperty("data", data.toString());
            }
        }

        try {
            org.eclipse.jetty.websocket.api.Session session = getSession();
            if (session != null && session.isOpen()) {
                session.getRemote().sendString(msg.toString());
            }
        } catch (IOException e) {
            logger.error("Failed to forward callback", e);
        }
    }
}
