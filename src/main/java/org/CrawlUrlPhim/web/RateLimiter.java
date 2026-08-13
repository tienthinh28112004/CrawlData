package org.CrawlUrlPhim.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimiter {
    private static final Logger logger = LoggerFactory.getLogger(RateLimiter.class);
    private static final int SHORT_LIMIT = 2;
    private static final long SHORT_WINDOW_MS = 5_000L;
    private static final int LONG_LIMIT = 10;
    private static final long LONG_WINDOW_MS = 60_000L;

    private final Map<String, Deque<Long>> requestHistory = new ConcurrentHashMap<>();

    public boolean isAllowed(String username) {
        Deque<Long> history = requestHistory.computeIfAbsent(username, k -> new ArrayDeque<>());
        synchronized (history) {
            long now = Instant.now().toEpochMilli();
            while (!history.isEmpty() && now - history.peekFirst() > LONG_WINDOW_MS) {
                history.pollFirst();
            }
            long shortWindowStart = now - SHORT_WINDOW_MS;
            long countInShortWindow = history.stream().filter(ts -> ts >= shortWindowStart).count();
            if (countInShortWindow >= SHORT_LIMIT) {
                logger.warn("Rate limit (short window) exceeded for user '{}': {} req in last {}ms", username, countInShortWindow, SHORT_WINDOW_MS);
                return false;
            }
            if (history.size() >= LONG_LIMIT) {
                logger.warn("Rate limit (long window) exceeded for user '{}': {} req in last {}ms", username, history.size(), LONG_WINDOW_MS);
                return false;
            }
            history.addLast(now);
            return true;
        }
    }
}
