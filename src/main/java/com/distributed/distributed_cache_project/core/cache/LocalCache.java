package com.distributed.distributed_cache_project.core.cache;

import com.distributed.distributed_cache_project.config.NodeConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class LocalCache {
    private static final Logger log = LoggerFactory.getLogger(LocalCache.class);

    private final Map<String,CacheEntry> store;
    private final int maxEntries;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);
    private final AtomicLong putCount = new AtomicLong(0);
    private final AtomicLong deleteCount = new AtomicLong(0);

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
        store.put(key, new CacheEntry(value, System.currentTimeMillis(), ttlMillis, System.currentTimeMillis()));
        putCount.incrementAndGet(); // Increment put count
        log.debug("LocalCache: Stored key '{}'. Current size: {}. Put Count: {}", key, store.size(), putCount.get());
    }
    public Object get(String key) {
        CacheEntry entry = store.get(key);
        if (entry == null) {
            missCount.incrementAndGet(); // Increment miss count
            log.debug("LocalCache: Key '{}' not found. Miss Count: {}", key, missCount.get());
            return null;
        }
        if (entry.isExpired()) {
            store.remove(key);
            missCount.incrementAndGet(); // Expired is also a miss
            log.info("LocalCache: Key '{}' expired and removed. Miss Count: {}", key, missCount.get());
            return null;
        }
        hitCount.incrementAndGet(); // Increment hit count
        log.debug("LocalCache: Retrieved key '{}'. Current size: {}. Hit Count: {}", key, store.size(), hitCount.get());
        return entry.getValue();
    }

    public void delete(String key) {
        store.remove(key);
        deleteCount.incrementAndGet(); // Increment delete count
        log.debug("LocalCache: Deleted key '{}'. Current size: {}. Delete Count: {}", key, store.size(), deleteCount.get());
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

    public long getHitCount() {
        return hitCount.get();
    }

    public long getMissCount() {
        return missCount.get();
    }

    public long getPutCount() {
        return putCount.get();
    }

    public long getDeleteCount() {
        return deleteCount.get();
    }

    public double getHitRatio() {
        long hits = hitCount.get();
        long total = hits + missCount.get();
        return total == 0 ? 0.0 : (double) hits / total;
    }

    // Get current JVM memory usage (approximate heap usage)
    public long getUsedMemoryBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    public long getTotalMemoryBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory();
    }
}
