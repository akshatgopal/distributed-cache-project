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
import java.util.stream.Collectors;

@Component
public class HashRing {
    private static final Logger log = LoggerFactory.getLogger(HashRing.class);
    private static final int VIRTUAL_NODES_PER_REAL_NODE = 100;
    private final TreeMap<Integer,Node> ring = new TreeMap<>();
    private final NodeConfigProperties nodeConfigProperties;

    private final int replicationFactor;
    @Getter
    private Node currentNode;


    public HashRing(NodeConfigProperties nodeConfigProperties){
        this.nodeConfigProperties = nodeConfigProperties;
        this.replicationFactor = nodeConfigProperties.getReplication().getFactor();
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
        List<Node> nodes = getNodesForKey(key);
        if (ring.isEmpty()) {
            throw new IllegalStateException("Hash ring is empty. Cannot determine owner node.");
        }

        return nodes.get(0);
    }

    public List<Node> getNodesInRing() {
        return new ArrayList<>(ring.values());
    }

    public int getRingSize() {
        return (int) ring.values().stream().distinct().count(); // Count distinct real nodes
    }

    /**
     * Determines the primary and replica nodes for a given key.
     * Nodes are ordered by their position on the ring.
     *
     * The cache key.
     * A list of Node objects, with the first being the primary, followed by replicas.
     * Returns an empty list if the ring is empty.
     */
    public List<Node> getNodesForKey(String key) {
        if (ring.isEmpty()) {
            log.warn("Attempted to get nodes for key '{}' from an empty HashRing.", key);
            return Collections.emptyList();
        }

        List<Node> responsibleNodes = new ArrayList<>();
        Set<Node> addedNodes = new HashSet<>(); // Use a set to avoid adding duplicate physical nodes if replication factor > actual unique nodes

        int keyHash = hash(key);

        // Find the starting point on the ring
        Map.Entry<Integer, Node> entry = ring.ceilingEntry(keyHash);
        if (entry == null) {
            entry = ring.firstEntry(); // Wrap around if no node found clockwise
        }

        // Iterate clockwise to find primary and replicas
        Map.Entry<Integer, Node> currentEntry = entry;
        for (int i = 0; responsibleNodes.size() < replicationFactor && i < ring.size() * 2; i++) { // Loop max twice the ring size to find enough unique nodes
            Node node = currentEntry.getValue();
            if (addedNodes.add(node)) { // Add if it's a new unique physical node
                responsibleNodes.add(node);
            }

            // Move to the next entry in the TreeMap (clockwise)
            currentEntry = ring.higherEntry(currentEntry.getKey());
            if (currentEntry == null) {
                currentEntry = ring.firstEntry(); // Wrap around
            }

            if (currentEntry == null || (i > ring.size() && responsibleNodes.size() < replicationFactor)) {
                // If the ring is too small or all unique nodes already added, break.
                // Or if we've looped too many times without finding enough unique nodes (e.g., ring too small).
                break;
            }
        }

        if (responsibleNodes.size() < replicationFactor) {
            log.warn("Could not find enough unique nodes ({}/{}) for key '{}' with replication factor {}. Current ring size: {} distinct nodes.",
                    responsibleNodes.size(), replicationFactor, key, replicationFactor, getRingSize());
        }

        log.debug("Nodes for key '{}' (Primary + Replicas): {}", key, responsibleNodes.stream().map(Node::getId).collect(Collectors.toList()));
        return responsibleNodes;
    }

}
