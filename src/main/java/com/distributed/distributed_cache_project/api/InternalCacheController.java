package com.distributed.distributed_cache_project.api;

import com.distributed.distributed_cache_project.service.CacheService;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/cache")
public class InternalCacheController {
    private static final Logger log = LoggerFactory.getLogger(InternalCacheController.class);
    private final CacheService cacheService;
    public InternalCacheController(CacheService cacheService){
        this.cacheService = cacheService;
    }

    @GetMapping("/{key}")
    public ResponseEntity<Object> internalGet(@PathVariable String key) {
        log.debug("Received internal GET request for key: {}", key);
        Object value = cacheService.get(key); // Delegates to LocalCache
        if (value != null) {
            return new ResponseEntity<>(value, HttpStatus.OK);
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @PostMapping("/{key}")
    public ResponseEntity<String> internalPut(@PathVariable String key,
                                              @RequestBody InternalCachePutRequest request) {
        log.debug("Received internal PUT request for key: {}", key);
        cacheService.put(key, request.getValue(), request.getTtlMillis()); // Delegates to LocalCache
        return new ResponseEntity<>("Key '" + key + "' stored internally.", HttpStatus.OK); // Use OK instead of CREATED for internal
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<String> internalDelete(@PathVariable String key) {
        log.debug("Received internal DELETE request for key: {}", key);
        cacheService.delete(key); // Delegates to LocalCache
        return new ResponseEntity<>("Key '" + key + "' deleted internally.", HttpStatus.NO_CONTENT);
    }

    @Data
    public static class InternalCachePutRequest {
        private Object value;
        private long ttlMillis;
    }

}
