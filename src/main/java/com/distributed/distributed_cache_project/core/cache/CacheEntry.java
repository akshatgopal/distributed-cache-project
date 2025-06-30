package com.distributed.distributed_cache_project.core.cache;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CacheEntry {
    private Object value;
    private long creationTimeMillis;
    private long ttlMillis; // 0 for no expiration, or specific milliseconds

    public boolean isExpired(){
        return ttlMillis > 0 && (System.currentTimeMillis() - creationTimeMillis) > ttlMillis;
    }
}
