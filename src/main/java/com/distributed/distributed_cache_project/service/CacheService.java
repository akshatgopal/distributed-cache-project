package com.distributed.distributed_cache_project.service;

import com.distributed.distributed_cache_project.core.cache.LocalCache;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CacheService {
    private final LocalCache localCache;

    public CacheService(LocalCache localCache){
        this.localCache = localCache;
    }

    public Object get(String key){
        return localCache.get(key);
    }

    public void put(String key, Object value, long ttlMillis) {
        localCache.put(key, value, ttlMillis);
    }

    public void delete(String key) {
        localCache.delete(key);
    }

    public int size() {
        return localCache.size();
    }

    public Map<String, Object> getAllCacheEntries() {
        return localCache.getAll();
    }
}
