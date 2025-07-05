package com.distributed.distributed_cache_project.api;

import com.distributed.distributed_cache_project.config.NodeConfigProperties;
import com.distributed.distributed_cache_project.core.cache.LocalCache;
import com.distributed.distributed_cache_project.core.consistenthashing.HashRing;
import com.distributed.distributed_cache_project.core.consistenthashing.Node;
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
    private final HashRing hashRing;
    private final Node currentNode;

    public InternalCacheController(CacheService cacheService, NodeDiscoveryService nodeDiscoveryService, HashRing hashRing, NodeConfigProperties nodeConfigProperties){
        this.cacheService = cacheService;
        this.nodeDiscoveryService = nodeDiscoveryService;
        this.hashRing = hashRing;
        NodeConfigProperties.NodeProperties currentProps = nodeConfigProperties.getNode();
        this.currentNode = new Node(currentProps.getHost() + ":" + currentProps.getPort(), currentProps.getHost(), currentProps.getPort());
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
    public Mono<ResponseEntity<String>> internalPut(@PathVariable String key,
                                                    @RequestBody InternalCachePutRequest request) {
        log.debug("InternalCacheController: Received internal PUT request for key: {}. Determining role...", key);

        Node primaryOwner = hashRing.getOwnerNode(key); // Get the primary owner for this key

        if (currentNode.equals(primaryOwner)) {
            // This node is the PRIMARY owner for this key.
            // It means this is either an initial client request that was forwarded to me,
            // or a local client request I am processing.
            // So, perform primary write logic (local store + replication).
            log.debug("InternalCacheController: This node is primary for key '{}'. Calling processPrimaryWrite.", key);
            return cacheService.processPrimaryWrite(key, request.getValue(), request.getTtlMillis())
                    .then(Mono.just(new ResponseEntity<>("Key '" + key + "' stored internally (primary).", HttpStatus.OK)))
                    .onErrorResume(e -> {
                        log.error("Internal PUT failed for primary key '{}': {}", key, e.getMessage());
                        return Mono.just(new ResponseEntity<>("Internal PUT failed for primary: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR));
                    });
        } else {
            // This node is NOT the primary owner for this key.
            // It MUST be a replica receiving a replication write from the primary.
            log.debug("InternalCacheController: This node is a replica for key '{}'. Calling processReplicaWrite.", key);
            return cacheService.processReplicaWrite(key, request.getValue(), request.getTtlMillis())
                    .then(Mono.just(new ResponseEntity<>("Key '" + key + "' stored internally (replica).", HttpStatus.OK)))
                    .onErrorResume(e -> {
                        log.error("Internal PUT failed for replica key '{}': {}", key, e.getMessage());
                        return Mono.just(new ResponseEntity<>("Internal PUT failed for replica: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR));
                    });
        }
    }

    @DeleteMapping("/{key}")
    public Mono<ResponseEntity<String>> internalDelete(@PathVariable String key) {
        log.debug("InternalCacheController: Received internal DELETE request for key: {}. Determining role...", key);

        Node primaryOwner = hashRing.getOwnerNode(key);

        if (currentNode.equals(primaryOwner)) {
            log.debug("InternalCacheController: This node is primary for key '{}'. Calling processPrimaryDelete.", key);
            return cacheService.processPrimaryDelete(key)
                    .then(Mono.just(new ResponseEntity<>("Key '" + key + "' deleted internally (primary).", HttpStatus.NO_CONTENT)))
                    .onErrorResume(e -> {
                        log.error("Internal DELETE failed for primary key '{}': {}", key, e.getMessage());
                        return Mono.just(new ResponseEntity<>("Internal DELETE failed for primary: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR));
                    });
        } else {
            log.debug("InternalCacheController: This node is a replica for key '{}'. Calling processReplicaDelete.", key);
            return cacheService.processReplicaDelete(key)
                    .then(Mono.just(new ResponseEntity<>("Key '" + key + "' deleted internally (replica).", HttpStatus.NO_CONTENT)))
                    .onErrorResume(e -> {
                        log.error("Internal DELETE failed for replica key '{}': {}", key, e.getMessage());
                        return Mono.just(new ResponseEntity<>("Internal DELETE failed for replica: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR));
                    });
        }
    }

    @PostMapping("/heartbeat") // NEW ENDPOINT
    public ResponseEntity<Void> internalHeartbeat(@RequestBody HeartbeatRequest request) {
//        log.info("Received heartbeat from node: {} at {}", request.getNodeId(), request.getTimestamp()); // Add this log
        nodeDiscoveryService.onHeartbeatReceived(request);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Data
    public static class InternalCachePutRequest {
        private Object value;
        private long ttlMillis;
    }

}
