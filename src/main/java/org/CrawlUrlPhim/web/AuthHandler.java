package org.CrawlUrlPhim.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class AuthHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(AuthHandler.class);
    private final AuthManager authManager;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public AuthHandler(AuthManager authManager) {
        this.authManager = authManager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, gson.toJson(Map.of("error", "Method not allowed")));
            return;
        }

        LoginRequest request;
        try (InputStream inputStream = exchange.getRequestBody()) {
            request = gson.fromJson(new java.io.InputStreamReader(inputStream, StandardCharsets.UTF_8), LoginRequest.class);
        }

        if (request == null || request.username() == null || request.username().isBlank() || request.password() == null || request.password().isBlank()) {
            sendResponse(exchange, 400, gson.toJson(Map.of("error", "username and password are required")));
            return;
        }

        String token = authManager.authenticate(request.username().trim(), request.password().trim());
        if (token == null) {
            sendResponse(exchange, 401, gson.toJson(Map.of("error", "Invalid username or password")));
            return;
        }

        logger.info("Login success for user '{}'", request.username());
        sendResponse(exchange, 200, gson.toJson(Map.of("token", token, "username", request.username())));
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
