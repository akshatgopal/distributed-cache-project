package com.distributed.distributed_cache_project.service;

import com.distributed.distributed_cache_project.config.NodeConfigProperties;
import com.distributed.distributed_cache_project.core.cache.LocalCache;
import com.distributed.distributed_cache_project.core.consistenthashing.HashRing;
import com.distributed.distributed_cache_project.core.consistenthashing.Node;
import com.distributed.distributed_cache_project.network.client.NodeApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    private final LocalCache localCache;
    private final HashRing hashRing;
    private final NodeApiClient nodeApiClient;
    private final Node currentNode;

    private final int replicationFactor;

    public CacheService(LocalCache localCache,
                        HashRing hashRing,
                        NodeApiClient nodeApiClient,
                        NodeConfigProperties nodeConfigProperties){
        this.localCache = localCache;
        this.hashRing = hashRing;
        this.nodeApiClient = nodeApiClient;
        NodeConfigProperties.NodeProperties currentProps = nodeConfigProperties.getNode();
        if(currentProps == null){
            throw new IllegalStateException("Current node properties (cache.node) are not configured for CacheService.");
        }
        this.currentNode = new Node(currentProps.getHost() + ":" + currentProps.getPort(), currentProps.getHost(), currentProps.getPort());
        this.replicationFactor = nodeConfigProperties.getReplication().getFactor();

        log.info("CacheService initialized. Current node: {}. Replication Factor: {}", currentNode, replicationFactor);
    }

    public Mono<String> get(String key) {
        Node ownerNode = hashRing.getOwnerNode(key);

        if (currentNode.equals(ownerNode)) {
            log.debug("Key '{}' belongs to this node. Retrieving locally.", key);
            // Convert immediate local result into a Mono
            Object value = localCache.get(key);
            return Mono.justOrEmpty(value != null ? value.toString() : null);
        } else {
            log.debug("Key '{}' belongs to node {}. Forwarding GET request.", key, ownerNode.getId());
            return nodeApiClient.forwardGet(ownerNode, key);
        }
    }

    public Mono<Void> put(String key, Object value, long ttlMillis) {
        List<Node> responsibleNodes = hashRing.getNodesForKey(key);
        if (responsibleNodes.isEmpty()) {
            log.error("No nodes found in HashRing for key '{}'. Cannot store.", key);
            return Mono.error(new IllegalStateException("No nodes available in cluster."));
        }

        Node primaryOwner = responsibleNodes.getFirst();

        if (currentNode.equals(primaryOwner)) {
            // This node is the primary owner
            log.debug("Key '{}' belongs to this node. Storing locally as primary.", key);
            localCache.put(key, value, ttlMillis); // Local store

            // Replicate to other responsible nodes (if replication factor > 1)
            if (replicationFactor > 1) {
                // Get replicas, excluding the primary owner (this node)
                List<Node> replicas = responsibleNodes.stream()
                        .filter(node -> !node.equals(currentNode))
                        .limit(replicationFactor - 1) // Only send to R-1 replicas
                        .toList();

                for (Node replica : replicas) {
                    log.debug("Replicating PUT for key '{}' to replica node: {}", key, replica.getId());
                    nodeApiClient.forwardPut(replica, key, value, ttlMillis)
                            .subscribeOn(Schedulers.parallel()) // Perform replication asynchronously
                            .doOnError(e -> log.warn("Failed to replicate PUT for key '{}' to {}: {}", key, replica.getId(), e.getMessage()))
                            .subscribe(); // Subscribe to trigger the reactive flow (asynchronous)
                }
            }
            return Mono.empty(); // Primary operation completes
        } else {
            // This node is not the primary owner, so forward to the primary
            log.debug("Key '{}' belongs to primary node {}. Forwarding PUT request.", key, primaryOwner.getId());
            return nodeApiClient.forwardPut(primaryOwner, key, value, ttlMillis);
        }
    }

    public Mono<Void> delete(String key) {
        List<Node> responsibleNodes = hashRing.getNodesForKey(key);
        if (responsibleNodes.isEmpty()) {
            log.warn("No nodes found in HashRing for key '{}'. Cannot delete.", key);
            return Mono.empty(); // Or Mono.error if you want to explicitly signal failure
        }

        Node primaryOwner = responsibleNodes.get(0);

        if (currentNode.equals(primaryOwner)) {
            log.debug("Key '{}' belongs to this node. Deleting locally as primary.", key);
            localCache.delete(key);

            // Propagate deletion to replicas (if replication factor > 1)
            if (replicationFactor > 1) {
                List<Node> replicas = responsibleNodes.stream()
                        .filter(node -> !node.equals(currentNode))
                        .limit(replicationFactor - 1)
                        .toList();

                for (Node replica : replicas) {
                    log.debug("Propagating DELETE for key '{}' to replica node: {}", key, replica.getId());
                    nodeApiClient.forwardDelete(replica, key)
                            .subscribeOn(Schedulers.parallel()) // Perform asynchronously
                            .doOnError(e -> log.warn("Failed to propagate DELETE for key '{}' to {}: {}", key, replica.getId(), e.getMessage()))
                            .subscribe();
                }
            }
            return Mono.empty();
        } else {
            log.debug("Key '{}' belongs to primary node {}. Forwarding DELETE request.", key, primaryOwner.getId());
            return nodeApiClient.forwardDelete(primaryOwner, key);
        }
    }

    public int size() {
        return localCache.size();
    }

    public Map<String, Object> getAllCacheEntries() {
        return localCache.getAll();
    }
}
