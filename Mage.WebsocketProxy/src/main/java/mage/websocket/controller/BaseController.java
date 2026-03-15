package mage.websocket.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public abstract class BaseController {

    private static final Logger logger = LoggerFactory.getLogger(BaseController.class);
    protected static final Gson gson = new Gson();

    protected final Session wsSession;

    protected BaseController(Session wsSession) {
        this.wsSession = wsSession;
    }

    public abstract String getPrefix();

    public abstract void handleMessage(String action, JsonObject payload);

    protected void sendToWs(String type, JsonObject payload) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", type);
        if (payload != null) {
            msg.add("payload", payload);
        }
        sendRaw(msg.toString());
    }

    protected void sendToWs(String type) {
        sendToWs(type, null);
    }

    protected void sendError(String message) {
        JsonObject payload = new JsonObject();
        payload.addProperty("error", message);
        sendToWs(getPrefix() + ".error", payload);
    }

    private void sendRaw(String json) {
        if (!wsSession.isOpen()) {
            logger.warn("WebSocket closed, cannot send: {}", json);
            return;
        }
        try {
            wsSession.getRemote().sendString(json);
        } catch (IOException e) {
            logger.error("Failed to send to WebSocket", e);
        }
    }
}
