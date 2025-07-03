package com.distributed.distributed_cache_project.core.consistenthashing;

import com.distributed.distributed_cache_project.config.NodeConfigProperties;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.apache.commons.codec.digest.MurmurHash3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class HashRing {
    private static final Logger log = LoggerFactory.getLogger(HashRing.class);
    private static final int VIRTUAL_NODES_PER_REAL_NODE = 100;
    private final TreeMap<Integer,Node> ring = new TreeMap<>();
    private final NodeConfigProperties nodeConfigProperties;
    @Getter
    private Node currentNode;

    public HashRing(NodeConfigProperties nodeConfigProperties){
        this.nodeConfigProperties = nodeConfigProperties;
    }

    @PostConstruct
    public void init(){
        NodeConfigProperties.NodeProperties currentProps = nodeConfigProperties.getNode();
        if (currentProps == null) {
            log.error("Failed to initialize HashRing: Current node properties (cache.node) are not configured.");
            throw new IllegalStateException("Current node properties must be configured in application.properties.");
        }
        this.currentNode = new Node(currentProps.getHost() + ":" + currentProps.getPort(), currentProps.getHost(), currentProps.getPort());
        log.info("HashRing initializing for current node: {}", currentNode);
        List<String> peers = nodeConfigProperties.getPeers();
        if (peers == null || peers.isEmpty()) {
            log.warn("No peer nodes configured in application.properties (cache.peers). Hash ring will contain only this node.");
            addRealNodeToRing(this.currentNode); // Add self if no peers are listed
        }else{
            for(String peerAddress : peers){
                try {
                    String[] parts = peerAddress.split(":");
                    if (parts.length == 2) {
                        String host = parts[0];
                        int port = Integer.parseInt(parts[1]);
                        // For peers, we might not have a specific 'id' from the config.
                        // Using a synthetic ID "host:port" for now, or you could infer from a map.
                        // For simplicity, let's use the address as the ID if not provided explicitly.
                        // In a real system, nodes would announce their unique ID.
                        Node peerNode = new Node(peerAddress, host, port); // Using address as ID for simplicity
                        addRealNodeToRing(peerNode);
                        log.info("Added peer node to HashRing: {}", peerNode);
                    } else {
                        log.warn("Invalid peer address format: {}. Expected host:port. Skipping.", peerAddress);
                    }
                } catch (NumberFormatException e) {
                    log.error("Invalid port number for peer address: {}. Skipping. Error: {}", peerAddress, e.getMessage());
                } catch (IllegalArgumentException e) {
                    log.error("Failed to create Node object for peer address: {}. Error: {}", peerAddress, e.getMessage());
                }
            }
        }
        if (ring.isEmpty()) {
            log.error("HashRing is empty after initialization! No nodes were added.");
            throw new IllegalStateException("HashRing must contain at least one node.");
        }
        log.info("HashRing initialized with {} nodes.", ring.size());
    }

    private int hash(String value){
        return MurmurHash3.hash32(value.getBytes(StandardCharsets.UTF_8));
    }

    public void addRealNodeToRing(Node node){
        if(node == null) {
            log.warn("Attempted to add a null node to the HashRing.");
            return;
        }
        for (int i = 0; i < VIRTUAL_NODES_PER_REAL_NODE; i++) {
            // Hash a combination of the node's address and the virtual node index
            int virtualNodeHash = hash(node.getAddress() + "-" + i);
            ring.put(virtualNodeHash, node); // Store the REAL node object
            log.debug("Added virtual node for {} at hash {}", node.getId(), virtualNodeHash);
        }
    }

    public void removeNode(Node node) {
        if (node == null) {
            log.warn("Attempted to remove a null node from the HashRing.");
            return;
        }
        List<Integer> hashesToRemove = new ArrayList<>();
        for (Map.Entry<Integer, Node> entry : ring.entrySet()) {
            if (entry.getValue().equals(node)) { // Use Node.equals() to find matching real nodes
                hashesToRemove.add(entry.getKey());
            }
        }
        for (Integer hash : hashesToRemove) {
            ring.remove(hash);
            log.debug("Removed virtual node for {} at hash {}", node.getId(), hash);
        }
        log.info("Node {} removed from ring ({} virtual nodes removed).", node.getId(), hashesToRemove.size());
    }

    public Node getOwnerNode(String key) {
        if (ring.isEmpty()) {
            throw new IllegalStateException("Hash ring is empty. Cannot determine owner node.");
        }

        int keyHash = hash(key);
        log.debug("Key '{}' hashed to {}", key, keyHash);

        // Find the node on the ring with a hash value greater than or equal to the key's hash.
        // This is the core of consistent hashing: move clockwise.
        Map.Entry<Integer, Node> entry = ring.ceilingEntry(keyHash);

        if (entry == null) {
            // If no such node exists, wrap around to the beginning of the ring (first node).
            entry = ring.firstEntry();
            log.debug("Wrapped around to first node: {}", entry.getValue().getId());
        }

        log.debug("Owner node for key '{}' is {}", key, entry.getValue().getId());
        return entry.getValue();
    }

    public List<Node> getNodesInRing() {
        return new ArrayList<>(ring.values());
    }

    public int getRingSize() {
        return (int) ring.values().stream().distinct().count(); // Count distinct real nodes
    }

}
