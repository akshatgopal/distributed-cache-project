package com.distributed.distributed_cache_project.config;

import com.distributed.distributed_cache_project.core.cache.LocalCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

    @Bean
    public LocalCache localCache(NodeConfigProperties nodeConfigProperties) { // <--- UPDATED METHOD SIGNATURE
        return new LocalCache(nodeConfigProperties); // <--- PASSING CONFIG
    }
}
