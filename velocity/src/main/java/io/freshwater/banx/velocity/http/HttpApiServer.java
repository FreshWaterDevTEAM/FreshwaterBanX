package io.freshwater.banx.velocity.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.freshwater.banx.api.BanEntry;
import io.freshwater.banx.velocity.punish.PunishmentService;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Minimal JSON REST API exposing ban data, backed by the JDK's built-in HTTP server.
 *
 * <ul>
 *   <li>GET /api/bans            - all active bans</li>
 *   <li>GET /api/bans/{uuid}     - active ban for a player (+ remaining time)</li>
 *   <li>GET /api/stats/today     - number of players banned today</li>
 * </ul>
 *
 * If a token is configured, requests must send {@code Authorization: Bearer <token>}.
 */
public final class HttpApiServer {

    private final PunishmentService service;
    private final String bind;
    private final int port;
    private final String token;
    private final Logger logger;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    private HttpServer server;

    public HttpApiServer(PunishmentService service, String bind, int port, String token, Logger logger) {
        this.service = service;
        this.bind = bind;
        this.port = port;
        this.token = token == null ? "" : token;
        this.logger = logger;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        server.createContext("/api/bans", this::handleBans);
        server.createContext("/api/stats/today", this::handleToday);
        server.setExecutor(Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "FreshwaterBanX-Http");
            t.setDaemon(true);
            return t;
        }));
        server.start();
        logger.info("HTTP API listening on {}:{}", bind, port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handleBans(HttpExchange exchange) throws IOException {
        if (reject(exchange)) {
            return;
        }
        try {
            String path = exchange.getRequestURI().getPath();
            String suffix = path.substring("/api/bans".length());
            if (suffix.startsWith("/")) {
                suffix = suffix.substring(1);
            }
            if (suffix.isBlank()) {
                List<Map<String, Object>> bans = service.listActiveBans().stream()
                        .map(HttpApiServer::toMap)
                        .collect(Collectors.toList());
                respond(exchange, 200, bans);
            } else {
                UUID uuid;
                try {
                    uuid = UUID.fromString(suffix);
                } catch (IllegalArgumentException e) {
                    respond(exchange, 400, Map.of("error", "invalid uuid"));
                    return;
                }
                Optional<BanEntry> ban = service.getActiveBan(uuid);
                if (ban.isPresent()) {
                    respond(exchange, 200, toMap(ban.get()));
                } else {
                    respond(exchange, 404, Map.of("banned", false, "uuid", uuid.toString()));
                }
            }
        } catch (Exception e) {
            logger.error("HTTP /api/bans error", e);
            respond(exchange, 500, Map.of("error", "internal error"));
        }
    }

    private void handleToday(HttpExchange exchange) throws IOException {
        if (reject(exchange)) {
            return;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("date", LocalDate.now().toString());
            body.put("count", service.countToday());
            respond(exchange, 200, body);
        } catch (Exception e) {
            logger.error("HTTP /api/stats/today error", e);
            respond(exchange, 500, Map.of("error", "internal error"));
        }
    }

    /** Returns true if the request was rejected (method/auth) and a response was already sent. */
    private boolean reject(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, Map.of("error", "method not allowed"));
            return true;
        }
        if (!token.isBlank()) {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (auth == null || !auth.equals("Bearer " + token)) {
                respond(exchange, 401, Map.of("error", "unauthorized"));
                return true;
            }
        }
        return false;
    }

    private void respond(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] data = gson.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    private static Map<String, Object> toMap(BanEntry entry) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entry.id());
        map.put("uuid", entry.playerId().toString());
        map.put("name", entry.playerName());
        map.put("type", entry.type().name());
        map.put("source", entry.source().name());
        map.put("reason", entry.reason());
        map.put("operator", entry.operator());
        map.put("hackType", entry.hackType().orElse(null));
        map.put("vl", entry.violationLevel());
        map.put("createdAt", entry.createdAt().toEpochMilli());
        map.put("expiresAt", entry.expiresAt().map(i -> (Object) i.toEpochMilli()).orElse(null));
        map.put("permanent", entry.permanent());
        long remaining = entry.remainingMillis();
        map.put("remainingMillis", remaining == Long.MAX_VALUE ? -1L : remaining);
        return map;
    }
}
