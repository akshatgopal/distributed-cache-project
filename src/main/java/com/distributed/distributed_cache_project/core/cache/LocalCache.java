package com.distributed.distributed_cache_project.core.cache;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LocalCache {
    private final ConcurrentHashMap<String,CacheEntry> store = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public LocalCache() {
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
        try{
            return store.size();
        }finally {
        }
    }

    public Map<String, Object> getAll() {
        Map<String, Object> allEntries = new HashMap<>();
        // Iterate safely over the ConcurrentHashMap.
        // It's crucial to still check for expiration here, as cleanup might not have run yet.
        store.forEach((key, entry) -> {
            if (!entry.isExpired()) {
                allEntries.put(key, entry.getValue());
            }
        });
        return Collections.unmodifiableMap(allEntries); // Return an unmodifiable map for safety
    }

    private void cleanupExpiredEntries() {
        System.out.println("Running TTL cleanup...");
        store.forEach((key, entry) -> {
            if (entry.isExpired()) {
                store.remove(key);
                System.out.println("Removed expired entry: " + key);
            }
        });
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}
