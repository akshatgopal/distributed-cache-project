package com.distributed.distributed_cache_project.core.cache;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CacheEntry {
    private Object value;
    private long creationTimeMillis;
    private long ttlMillis;
    private long lastModifiedTimeMillis;

    public CacheEntry(Object value, long creationTimeMillis, long ttlMillis){
        this(value,creationTimeMillis,ttlMillis,creationTimeMillis);
    }// 0 for no expiration, or specific milliseconds

    public boolean isExpired(){
        return ttlMillis > 0 && (System.currentTimeMillis() - creationTimeMillis) > ttlMillis;
    }

}
