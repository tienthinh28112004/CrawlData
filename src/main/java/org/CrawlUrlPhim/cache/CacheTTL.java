package org.CrawlUrlPhim.cache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class CacheTTL<K, V> implements Map<K, V> {
    private final long idleTtlMillis;
    private final long writeTtlMillis;
    private final Map<K, CacheEntry<V>> entries = new ConcurrentHashMap<>();
    private final AtomicLong requests = new AtomicLong();
    private final AtomicLong hits = new AtomicLong();

    public CacheTTL(int idleTtlSeconds, int writeTtlSeconds) {
        if (idleTtlSeconds <= 0 || writeTtlSeconds <= 0) {
            throw new IllegalArgumentException("TTL values must be positive");
        }
        this.idleTtlMillis = idleTtlSeconds * 1000L;
        this.writeTtlMillis = writeTtlSeconds * 1000L;
    }

    @Override
    public V get(Object key) {
        requests.incrementAndGet();
        CacheEntry<V> entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired(now())) {
            entries.remove(key, entry);
            return null;
        }
        hits.incrementAndGet();
        entry.touch();
        return entry.value;
    }

    @Override
    public V put(K key, V value) {
        Objects.requireNonNull(key, "key");
        CacheEntry<V> previous = entries.put(key, new CacheEntry<>(value, now()));
        return previous == null ? null : previous.value;
    }

    public Map<K, V> getMap() {
        cleanupExpiredEntries();
        Map<K, V> snapshot = new LinkedHashMap<>();
        for (Map.Entry<K, CacheEntry<V>> entry : entries.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().value);
        }
        return Collections.unmodifiableMap(snapshot);
    }

    public int getHitRate() {
        long requestCount = requests.get();
        if (requestCount == 0) {
            return 0;
        }
        double ratio = (double) hits.get() * 100.0 / requestCount;
        return (int) Math.round(ratio);
    }

    public void shutdown() {
        clear();
    }

    @Override
    public int size() {
        cleanupExpiredEntries();
        return entries.size();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        cleanupExpiredEntries();
        return entries.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        cleanupExpiredEntries();
        for (CacheEntry<V> entry : entries.values()) {
            if (Objects.equals(entry.value, value)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public V remove(Object key) {
        CacheEntry<V> removed = entries.remove(key);
        return removed == null ? null : removed.value;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        for (Map.Entry<? extends K, ? extends V> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clear() {
        entries.clear();
        requests.set(0);
        hits.set(0);
    }

    @Override
    public Set<K> keySet() {
        cleanupExpiredEntries();
        return Collections.unmodifiableSet(entries.keySet());
    }

    @Override
    public Collection<V> values() {
        cleanupExpiredEntries();
        List<V> values = new ArrayList<>();
        for (CacheEntry<V> entry : entries.values()) {
            values.add(entry.value);
        }
        return Collections.unmodifiableList(values);
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        cleanupExpiredEntries();
        Map<K, V> snapshot = new LinkedHashMap<>();
        for (Map.Entry<K, CacheEntry<V>> entry : entries.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().value);
        }
        return Collections.unmodifiableSet(snapshot.entrySet());
    }

    private void cleanupExpiredEntries() {
        long now = now();
        for (Map.Entry<K, CacheEntry<V>> entry : entries.entrySet()) {
            if (entry.getValue().isExpired(now)) {
                entries.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private long now() {
        return System.currentTimeMillis();
    }

    private final class CacheEntry<T> {
        private final T value;
        private final long createdAt;
        private volatile long lastAccessAt;

        private CacheEntry(T value, long timestamp) {
            this.value = value;
            this.createdAt = timestamp;
            this.lastAccessAt = timestamp;
        }

        private void touch() {
            lastAccessAt = now();
        }

        private boolean isExpired(long timestamp) {
            return timestamp - lastAccessAt > idleTtlMillis
                    || timestamp - createdAt > writeTtlMillis;
        }
    }
}
