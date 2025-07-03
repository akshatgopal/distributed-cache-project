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

import java.util.Map;

@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    private final LocalCache localCache;
    private final HashRing hashRing;
    private final NodeApiClient nodeApiClient;
    private final Node currentNode;

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
        log.info("CacheService initialized. Current node: {}", currentNode);
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
        Node ownerNode = hashRing.getOwnerNode(key);
        log.debug(currentNode.getAddress(),currentNode.getId());
        log.debug("It enters this area!!");
        if (currentNode.equals(ownerNode)) {
            log.debug("Key '{}' belongs to this node. Storing locally.", key);
            localCache.put(key, value, ttlMillis);
            return Mono.empty(); // Representing void completion
        } else {
            log.debug("Key '{}' belongs to node {}. Forwarding PUT request.", key, ownerNode.getId());
            return nodeApiClient.forwardPut(ownerNode, key, value, ttlMillis);
        }
    }

    public Mono<Void> delete(String key) {
        Node ownerNode = hashRing.getOwnerNode(key);

        if (currentNode.equals(ownerNode)) {
            log.debug("Key '{}' belongs to this node. Deleting locally.", key);
            localCache.delete(key);
            return Mono.empty(); // Representing void completion
        } else {
            log.debug("Key '{}' belongs to node {}. Forwarding DELETE request.", key, ownerNode.getId());
            return nodeApiClient.forwardDelete(ownerNode, key);
        }
    }

    public int size() {
        return localCache.size();
    }

    public Map<String, Object> getAllCacheEntries() {
        return localCache.getAll();
    }
}
