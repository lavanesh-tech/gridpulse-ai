package com.gridpulse.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** Thin GridPulse REST client: signs in once, re-signs in once on 401 (token expiry), and returns raw JSON. */
public class GridPulseClient {
    private final RestClient http;
    private final GridPulseProperties props;
    private volatile String token;

    public GridPulseClient(RestClient.Builder builder, GridPulseProperties props) {
        this.http = builder.baseUrl(props.apiUrl()).build();
        this.props = props;
    }

    public JsonNode get(String path, Object... vars) {
        return withToken(t -> http.get().uri(path, vars).header("Authorization", "Bearer " + t)
                .retrieve().body(JsonNode.class));
    }

    public JsonNode post(String path, Object body) {
        return withToken(t -> http.post().uri(path).header("Authorization", "Bearer " + t)
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class));
    }

    private interface Call {
        JsonNode run(String token);
    }

    private JsonNode withToken(Call call) {
        if (token == null) signIn();
        try {
            return call.run(token);
        } catch (HttpClientErrorException.Unauthorized e) {
            signIn();
            return call.run(token);
        }
    }

    private synchronized void signIn() {
        if (props.username() == null || props.username().isBlank()) {
            throw new IllegalStateException("Set GRIDPULSE_USERNAME and GRIDPULSE_PASSWORD for the MCP server");
        }
        JsonNode r = http.post().uri("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", props.username(), "password", props.password()))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    throw new IllegalStateException("GridPulse rejected the MCP server's credentials (" + res.getStatusCode() + ")");
                })
                .body(JsonNode.class);
        token = r.get("accessToken").asText();
    }
}
