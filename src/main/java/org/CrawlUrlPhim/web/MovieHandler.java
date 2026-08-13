package org.CrawlUrlPhim.web;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheStats;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.CrawlUrlPhim.db.DatabaseManager;
import org.CrawlUrlPhim.model.Movie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MovieHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(MovieHandler.class);
    private static final int CACHE_IDLE_TTL_SECONDS = 10;
    private static final int CACHE_WRITE_TTL_SECONDS = 20;

    private final DatabaseManager db;
    private final AuthManager authManager;
    private final RateLimiter rateLimiter;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Cache<String, Movie> cache = CacheBuilder.newBuilder()
            .expireAfterAccess(CACHE_IDLE_TTL_SECONDS, TimeUnit.SECONDS)
            .expireAfterWrite(CACHE_WRITE_TTL_SECONDS, TimeUnit.SECONDS)
            .recordStats()
            .build();

    public MovieHandler(DatabaseManager db, AuthManager authManager, RateLimiter rateLimiter) {
        this.db = db;
        this.authManager = authManager;
        this.rateLimiter = rateLimiter;
        logger.info("MovieHandler started - Guava cache idleTTL={}s writeTTL={}s",
                CACHE_IDLE_TTL_SECONDS, CACHE_WRITE_TTL_SECONDS);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, buildError("Method not allowed"));
            return;
        }

        String username = resolveUser(exchange);
        if (username == null) {
            sendResponse(exchange, 401, buildError("Unauthorized. Please login via POST /login and provide 'Authorization: Bearer <token>' header."));
            return;
        }

        if (!rateLimiter.isAllowed(username)) {
            sendResponse(exchange, 429, buildError("Too Many Requests. Limit: 2 requests per 5s and 10 requests per 1 minute."));
            return;
        }

        String movieUrl = parseUrlParam(exchange.getRequestURI());
        if (movieUrl == null || movieUrl.isBlank()) {
            sendResponse(exchange, 400, buildError("Missing required parameter: url"));
            return;
        }

        logger.info("Request: GET /movie?url={} (user={})", movieUrl, username);

        Movie movie = cache.getIfPresent(movieUrl);
        if (movie != null) {
            logger.info("Cache HIT for url={} (hitRate={}%)", movieUrl, cacheHitRate());
            sendResponse(exchange, 200, gson.toJson(movie));
            return;
        }

        logger.info("Cache MISS for url={} - querying DB", movieUrl);
        movie = db.getMovieByUrl(movieUrl);
        if (movie == null) {
            sendResponse(exchange, 404, buildError("Movie not found for URL: " + movieUrl));
            return;
        }

        cache.put(movieUrl, movie);
        sendResponse(exchange, 200, gson.toJson(movie));
    }

    private String resolveUser(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring("Bearer ".length()).trim();
        return authManager.validateToken(token);
    }

    private String parseUrlParam(URI requestUri) {
        String query = requestUri.getRawQuery();
        if (query == null) {
            return null;
        }
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && "url".equals(kv[0])) {
                return java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String buildError(String message) {
        return gson.toJson(Map.of("error", message));
    }

    private int cacheHitRate() {
        CacheStats stats = cache.stats();
        return (int) Math.round(stats.hitRate() * 100.0);
    }
}
