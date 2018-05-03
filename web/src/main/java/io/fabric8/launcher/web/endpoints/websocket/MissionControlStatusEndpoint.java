package io.fabric8.launcher.web.endpoints.websocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.enterprise.context.Dependent;
import javax.enterprise.event.Observes;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.websocket.server.PathParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.fabric8.launcher.base.JsonUtils;
import io.fabric8.launcher.core.api.events.StatusEventType;
import io.fabric8.launcher.core.api.events.StatusMessageEvent;

/**
 * A websocket based resource that informs clients about the status of the operations
 *
 * https://abhirockzz.wordpress.com/2015/02/10/integrating-cdi-and-websockets/
 */
@Dependent
@Path(value = "/status")
public class MissionControlStatusEndpoint {
    private static final Logger logger = Logger.getLogger(MissionControlStatusEndpoint.class.getName());

    private static final Map<UUID, List<String>> messageBuffer = new ConcurrentHashMap<>();

    @GET
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    public JsonArray init() {
        JsonArrayBuilder builder = Json.createArrayBuilder();
        for (StatusEventType statusEventType : StatusEventType.values()) {
            JsonObjectBuilder object = Json.createObjectBuilder();
            builder.add(object.add(statusEventType.name(), statusEventType.getMessage()).build());
        }
        return builder.build();
    }

    @GET
    @Path("/{uuid}")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> messages(@PathParam("uuid") String uuid) {
        UUID key = UUID.fromString(uuid);
        List<String> messages = messageBuffer.remove(key);
        return messages;
    }

    /**
     * Listen to status changes and pushes them to the registered sessions
     *
     * @param msg the status message to be send
     * @throws IOException when message could not be serialized to JSON
     */
    public void onEvent(@Observes StatusMessageEvent msg) throws IOException {
        UUID msgId = msg.getId();
        String message = JsonUtils.toString(msg);
        List<String> messages = messageBuffer.computeIfAbsent(msgId, k -> new ArrayList<>());
        messages.add(message);
    }
}