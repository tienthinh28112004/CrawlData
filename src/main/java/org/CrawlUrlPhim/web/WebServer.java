package org.CrawlUrlPhim.web;

import com.sun.net.httpserver.HttpServer;
import org.CrawlUrlPhim.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebServer {
    private static final Logger logger = LoggerFactory.getLogger(WebServer.class);
    private static final int PORT = 8080;

    private final HttpServer server;
    private final ExecutorService executor;

    public WebServer(DatabaseManager db) throws Exception {
        AuthManager authManager = new AuthManager();
        RateLimiter rateLimiter = new RateLimiter();
        server = HttpServer.create(new InetSocketAddress(PORT), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.createContext("/login", new AuthHandler(authManager));
        server.createContext("/movie", new MovieHandler(db, authManager, rateLimiter));
        server.createContext("/prime", new PrimeHandler(authManager, rateLimiter));
        server.setExecutor(executor);
    }

    public void start() {
        server.start();
        logger.info("CrawlUrl server started on http://localhost:{}", PORT);
        logger.info("POST /login");
        logger.info("GET /movie?url={{url}}");
        logger.info("GET /prime?n=10000");
        logger.info("Rate limits: 2 req/5s and 10 req/1min per user");
        logger.info("Press Ctrl+C to stop.");
    }

    public void stop() {
        server.stop(1);
        executor.shutdown();
        logger.info("Web server stopped.");
    }
}
