package com.distributed.distributed_cache_project.api;

import com.distributed.distributed_cache_project.network.discovery.NodeDiscoveryService;
import com.distributed.distributed_cache_project.network.model.HeartbeatRequest;
import com.distributed.distributed_cache_project.service.CacheService;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/internal/cache")
public class InternalCacheController {
    private static final Logger log = LoggerFactory.getLogger(InternalCacheController.class);
    private final CacheService cacheService;
    private  final NodeDiscoveryService nodeDiscoveryService;
    public InternalCacheController(CacheService cacheService,NodeDiscoveryService nodeDiscoveryService){
        this.cacheService = cacheService;
        this.nodeDiscoveryService = nodeDiscoveryService;
    }

    @GetMapping("/{key}")
    public Mono<ResponseEntity<String>> internalGet(@PathVariable String key) { // <--- Return Mono<ResponseEntity<String>>
        log.debug("Received internal GET request for key: {}", key);
        return cacheService.get(key) // This now returns Mono<String>
                .map(value -> {
                    log.debug("InternalCacheController: Successfully mapped internal value for key: {}. Value: {}", key, value);
                    return new ResponseEntity<>(value, HttpStatus.OK);
                })
                .defaultIfEmpty(new ResponseEntity<>(HttpStatus.NOT_FOUND)); // If Mono is empty, return 404
    }

    @PostMapping("/{key}")
    public Mono<ResponseEntity<String>> internalPut(@PathVariable String key, // <--- Change return type to Mono<ResponseEntity<String>>
                                                    @RequestBody InternalCachePutRequest request) {
        log.debug("Received internal PUT request for key: {}", key);
        return cacheService.put(key, request.getValue(), request.getTtlMillis()) // Now returns Mono<Void>
                .then(Mono.just(new ResponseEntity<>("Key '" + key + "' stored internally.", HttpStatus.OK)))
                .onErrorResume(e -> {
                    log.error("Internal PUT failed for key '{}': {}", key, e.getMessage());
                    return Mono.just(new ResponseEntity<>("Internal PUT failed: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR));
                });
    }

    @DeleteMapping("/{key}")
    public Mono<ResponseEntity<String>> internalDelete(@PathVariable String key) { // <--- Change return type to Mono<ResponseEntity<String>>
        log.debug("Received internal DELETE request for key: {}", key);
        return cacheService.delete(key) // Now returns Mono<Void>
                .then(Mono.just(new ResponseEntity<>("Key '" + key + "' deleted internally.", HttpStatus.NO_CONTENT)))
                .onErrorResume(e -> {
                    log.error("Internal DELETE failed for key '{}': {}", key, e.getMessage());
                    return Mono.just(new ResponseEntity<>("Internal DELETE failed: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR));
                });
    }
    @PostMapping("/heartbeat") // NEW ENDPOINT
    public ResponseEntity<Void> internalHeartbeat(@RequestBody HeartbeatRequest request) {
        log.info("Received heartbeat from node: {} at {}", request.getNodeId(), request.getTimestamp()); // Add this log
        nodeDiscoveryService.onHeartbeatReceived(request);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Data
    public static class InternalCachePutRequest {
        private Object value;
        private long ttlMillis;
    }

}
