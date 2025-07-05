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
            // This node IS the primary owner for an initial client request.
            // It will store locally and THEN initiate replication.
            log.debug("CacheService: Key '{}' belongs to this node. Processing as primary owner's initial PUT.", key);
            // Call internal method to process locally and then replicate
            return processPrimaryWrite(key, value, ttlMillis); // <--- NEW METHOD CALL
        } else {
            // This node is NOT the primary owner, so forward the initial client request to the primary.
            log.debug("CacheService: Key '{}' belongs to primary node {}. Forwarding initial client PUT request.", key, primaryOwner.getId());
            return nodeApiClient.forwardPut(primaryOwner, key, value, ttlMillis); // This call hits InternalCacheController on primary
        }
    }

    public Mono<Void> delete(String key) {
        List<Node> responsibleNodes = hashRing.getNodesForKey(key);
        if (responsibleNodes.isEmpty()) {
            log.warn("CacheService: No nodes found in HashRing for key '{}'. Cannot delete.", key);
            return Mono.empty();
        }

        Node primaryOwner = responsibleNodes.getFirst();

        if (currentNode.equals(primaryOwner)) {
            log.debug("CacheService: Key '{}' belongs to this node. Processing as primary owner's initial DELETE.", key);
            return processPrimaryDelete(key); // <--- NEW METHOD CALL
        } else {
            log.debug("CacheService: Key '{}' belongs to primary node {}. Forwarding initial client DELETE request.", key, primaryOwner.getId());
            return nodeApiClient.forwardDelete(primaryOwner, key);
        }
    }

    public int size() {
        return localCache.size();
    }

    public Map<String, Object> getAllCacheEntries() {
        return localCache.getAll();
    }

    public Mono<Void> processPrimaryWrite(String key, Object value, long ttlMillis) {
        log.debug("CacheService (Primary Write): Storing key '{}' locally.", key);
        localCache.put(key, value, ttlMillis); // Store locally on the primary

        // Replicate to other responsible nodes (if replication factor > 1)
        if (replicationFactor > 1) {
            List<Node> responsibleNodes = hashRing.getNodesForKey(key);
            List<Node> replicas = responsibleNodes.stream()
                    .filter(node -> !node.equals(currentNode)) // Exclude primary
                    .limit(replicationFactor - 1)
                    .toList();

            if (replicas.isEmpty() && responsibleNodes.size() > 1) {
                log.warn("CacheService (Primary Write): Replication factor is {}, but no distinct replica nodes found after filtering primary for key '{}'. Responsible nodes: {}",
                        replicationFactor, key, responsibleNodes.stream().map(Node::getId).collect(Collectors.toList()));
            }

            for (Node replica : replicas) {
                log.debug("CacheService (Primary Write): Replicating PUT for key '{}' to replica node: {}", key, replica.getId());
                // Call NodeApiClient to forward to replica. This will hit InternalCacheController on replica.
                nodeApiClient.forwardPut(replica, key, value, ttlMillis)
                        .subscribeOn(Schedulers.parallel()) // Perform replication asynchronously
                        .doOnError(e -> log.warn("CacheService (Primary Write): Failed to replicate PUT for key '{}' to {}: {}", key, replica.getId(), e.getMessage()))
                        .subscribe(); // Subscribe to trigger the reactive flow
            }
        }
        return Mono.empty(); // Primary operation completes immediately
    }

    /**
     * Handles local deletion and propagates deletion to replicas.
     * This method is called ONLY by the node that is the PRIMARY owner for the key.
     */
    public Mono<Void> processPrimaryDelete(String key) {
        log.debug("CacheService (Primary Delete): Deleting key '{}' locally.", key);
        localCache.delete(key); // Delete locally on the primary

        // Propagate deletion to replicas
        if (replicationFactor > 1) {
            List<Node> responsibleNodes = hashRing.getNodesForKey(key);
            List<Node> replicas = responsibleNodes.stream()
                    .filter(node -> !node.equals(currentNode))
                    .limit(replicationFactor - 1)
                    .toList();

            for (Node replica : replicas) {
                log.debug("CacheService (Primary Delete): Propagating DELETE for key '{}' to replica node: {}", key, replica.getId());
                nodeApiClient.forwardDelete(replica, key)
                        .subscribeOn(Schedulers.parallel())
                        .doOnError(e -> log.warn("CacheService (Primary Delete): Failed to propagate DELETE for key '{}' to {}: {}", key, replica.getId(), e.getMessage()))
                        .subscribe();
            }
        }
        return Mono.empty();
    }

    /**
     * This method is called ONLY when a node is a *replica* receiving a replication write.
     * It only performs local storage, without any further routing or replication.
     * This is the dedicated entry point for internal PUT requests representing replicated data.
     */
    public Mono<Void> processReplicaWrite(String key, Object value, long ttlMillis) {
        log.debug("CacheService (Replica Write): Storing key '{}' locally as replica.", key);
        localCache.put(key, value, ttlMillis);
        return Mono.empty();
    }

    /**
     * This method is called ONLY when a node is a *replica* receiving a replication delete.
     * It only performs local deletion, without any further routing or propagation.
     * This is the dedicated entry point for internal DELETE requests representing replicated data.
     */
    public Mono<Void> processReplicaDelete(String key) {
        log.debug("CacheService (Replica Delete): Deleting key '{}' locally as replica.", key);
        localCache.delete(key);
        return Mono.empty();
    }
}
