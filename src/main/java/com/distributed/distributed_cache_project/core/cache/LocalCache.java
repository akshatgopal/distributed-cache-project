package com.distributed.distributed_cache_project.core.cache;

import com.distributed.distributed_cache_project.config.NodeConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LocalCache {
    private static final Logger log = LoggerFactory.getLogger(LocalCache.class);

    private final Map<String,CacheEntry> store;
    private final int maxEntries;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public LocalCache(NodeConfigProperties nodeConfigProperties) {
        int nodeMaxEntries = nodeConfigProperties.getCapacity().getMaxEntries();
        if (nodeMaxEntries <= 0) {
            log.warn("Cache max-entries configured as {}. Setting to default 1000.", nodeMaxEntries);
            this.maxEntries = 1000; // Fallback default
        }else{
            this.maxEntries = nodeMaxEntries;
        }

        this.store = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) { // <--- LRU IMPLEMENTATION
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                // This method is called after a new entry is added or an existing entry is accessed.
                // Return true if the eldest entry should be removed.
                boolean shouldEvict = size() > maxEntries;
                if (shouldEvict) {
                    log.info("Evicting LRU entry: Key '{}' due to cache exceeding max entries ({} > {}).", eldest.getKey(), size(), maxEntries);
                }
                return shouldEvict;
            }
        });
        // Schedule cleanup task
        scheduler.scheduleAtFixedRate(this::cleanupExpiredEntries, 1, 5, TimeUnit.MINUTES);
    }
    public void put(String key, Object value, long ttlMillis) {
        try {
            store.put(key, new CacheEntry(value, System.currentTimeMillis(), ttlMillis,System.currentTimeMillis()));
        } finally {
        }
    }
    public Object get(String key) {
        try {
            CacheEntry entry = store.get(key);
            if (entry == null) {
                return null;
            }
            if (entry.isExpired()) {
                store.remove(key);
                return null;
            }
            return entry.getValue();
        } finally {
        }
    }

    public void delete(String key) {
        try {
            store.remove(key);
        } finally {
        }
    }

    public int size(){
        return (int) store.entrySet().stream().filter(e -> !e.getValue().isExpired()).count();
    }

    public Map<String, Object> getAll() {
        Map<String, Object> allEntries = new LinkedHashMap<>(); // Use LinkedHashMap to preserve order for getAll()
        // Concurrent iteration over synchronizedMap is generally safe but might require external lock for consistency
        // For simplicity and correctness here:
        synchronized (store) { // Lock the map during iteration to ensure consistency
            store.forEach((key, entry) -> {
                if (entry != null && !entry.isExpired()) {
                    allEntries.put(key, entry.getValue());
                } else if (entry != null) { // If expired during iteration
                    store.remove(key); // Proactively remove
                    log.debug("LocalCache: Proactively removed expired key '{}' during getAll.", key);
                }
            });
        }
        log.debug("LocalCache: Retrieved all {} entries.", allEntries.size());
        return allEntries; // Return an unmodifiable map for safety
    }

    private void cleanupExpiredEntries() {
        log.info("LocalCache: Running TTL cleanup. Initial size: {}", store.size());
        // Use a safe iteration for ConcurrentHashMap (which LinkedHashMap does not directly support like this for concurrent removal)
        // With synchronizedMap, external synchronization is safer.
        synchronized (store) {
            store.entrySet().removeIf(entry -> {
                if (entry.getValue() != null && entry.getValue().isExpired()) {
                    log.debug("LocalCache: Removed expired entry: {}", entry.getKey());
                    return true; // Remove this entry
                }
                return false;
            });
        }
        log.info("LocalCache: TTL cleanup complete. Final size: {}", store.size());
    }

    public void shutdown() {
        scheduler.shutdownNow();
        log.info("LocalCache scheduler shut down.");
    }
}
