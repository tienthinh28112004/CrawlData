package org.CrawlUrlPhim.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PrimeHandler implements HttpHandler {
    private final AuthManager authManager;
    private final RateLimiter rateLimiter;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public PrimeHandler(AuthManager authManager, RateLimiter rateLimiter) {
        this.authManager = authManager;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, gson.toJson(Map.of("error", "Method not allowed")));
            return;
        }

        String username = resolveUser(exchange);
        if (username == null) {
            sendResponse(exchange, 401, gson.toJson(Map.of("error", "Unauthorized. Please login first.")));
            return;
        }

        if (!rateLimiter.isAllowed(username)) {
            sendResponse(exchange, 429, gson.toJson(Map.of("error", "Too Many Requests. Limit: 2 requests per 5s and 10 requests per 1 minute.")));
            return;
        }

        int n = parseIntParam(exchange.getRequestURI(), "n", 10000);
        if (n < 2) {
            sendResponse(exchange, 400, gson.toJson(Map.of("error", "n must be >= 2")));
            return;
        }

        List<Integer> primes = primesUpTo(n);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("user", username);
        response.put("n", n);
        response.put("count", primes.size());
        response.put("primes", primes);
        sendResponse(exchange, 200, gson.toJson(response));
    }

    private String resolveUser(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authManager.validateToken(authHeader.substring("Bearer ".length()).trim());
    }

    private int parseIntParam(URI uri, String name, int defaultValue) {
        String query = uri.getRawQuery();
        if (query == null) {
            return defaultValue;
        }
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && name.equals(kv[0])) {
                try {
                    return Math.max(1, Integer.parseInt(kv[1]));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return defaultValue;
    }

    private List<Integer> primesUpTo(int n) {
        boolean[] composite = new boolean[n + 1];
        java.util.ArrayList<Integer> primes = new java.util.ArrayList<>();
        for (int i = 2; i <= n; i++) {
            if (!composite[i]) {
                primes.add(i);
                if ((long) i * i <= n) {
                    for (int j = i * i; j <= n; j += i) {
                        composite[j] = true;
                    }
                }
            }
        }
        return primes;
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
