package org.CrawlUrlPhim.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AuthManager {
    private static final Logger logger = LoggerFactory.getLogger(AuthManager.class);

    private static final Map<String, String> USERS = Map.of(
            "admin", "admin123",
            "user1", "pass1"
    );

    private final Map<String, String> activeTokens = new ConcurrentHashMap<>();

    public String authenticate(String username, String password) {
        if (username == null || password == null) {
            return null;
        }
        String expected = USERS.get(username);
        if (expected == null || !expected.equals(password)) {
            logger.warn("Failed login attempt for user '{}'", username);
            return null;
        }
        String token = UUID.randomUUID().toString();
        activeTokens.put(token, username);
        logger.info("User '{}' logged in successfully, token issued.", username);
        return token;
    }

    public String validateToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return activeTokens.get(token);
    }
}
