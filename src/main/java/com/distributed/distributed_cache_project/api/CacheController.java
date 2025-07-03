package com.distributed.distributed_cache_project.api;


import com.distributed.distributed_cache_project.service.CacheService;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/cache")
public class CacheController {
    private final CacheService cacheService;
    private static final Logger log = LoggerFactory.getLogger(CacheController.class);

    public CacheController(CacheService cacheService){
        this.cacheService = cacheService;
    }

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> getAll() {
        Map<String, Object> allEntries = cacheService.getAllCacheEntries();
        return new ResponseEntity<>(allEntries, HttpStatus.OK);
    }

    @GetMapping("/{key}")
    public Mono<ResponseEntity<String>> get(@PathVariable String key) {
        return cacheService.get(key) // CacheService now returns Mono<Object>
                .map(value -> {
                    log.info("CacheController: Successfully mapped value to ResponseEntity for key: {}. Value type: {}", key, value != null ? value.getClass().getName() : "null"); // <-- SET BREAKPOINT HERE
                    return new ResponseEntity<>(value, HttpStatus.OK);
                }) // If value emitted, return OK
                .defaultIfEmpty(new ResponseEntity<>(HttpStatus.NOT_FOUND)); // If Mono is empty (404/not found), return NOT_FOUND
    }

    @PostMapping("/{key}")
    public Mono<ResponseEntity<String>> put(@PathVariable String key,
                                            @RequestBody CachePutRequest request) {
        return cacheService.put(key, request.getValue(), request.getTtlMillis()) // CacheService now returns Mono<Void>
                .then(Mono.just(new ResponseEntity<>("Key '" + key + "' stored successfully.", HttpStatus.CREATED)))
                .onErrorResume(e -> {
                    // Basic error handling: if something goes wrong during put/forward, return 500
                    return Mono.just(new ResponseEntity<>("Failed to store key '" + key + "': " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR));
                });
    }

    @DeleteMapping("/{key}")
    public Mono<ResponseEntity<String>> delete(@PathVariable String key) {
        return cacheService.delete(key) // CacheService now returns Mono<Void>
                .then(Mono.just(new ResponseEntity<>("Key '" + key + "' deleted.", HttpStatus.NO_CONTENT)))
                .onErrorResume(e -> {
                    return Mono.just(new ResponseEntity<>("Failed to delete key '" + key + "': " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR));
                });
    }

    @Data
    public static class CachePutRequest {
        private Object value;
        private long ttlMillis;
    }
}


