package com.distributed.distributed_cache_project.network.discovery;

import com.distributed.distributed_cache_project.config.NodeConfigProperties;
import com.distributed.distributed_cache_project.core.consistenthashing.HashRing;
import com.distributed.distributed_cache_project.core.consistenthashing.Node;
import com.distributed.distributed_cache_project.network.client.NodeApiClient;
import com.distributed.distributed_cache_project.network.model.HeartbeatRequest;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class NodeDiscoveryService {
    private static final Logger log = LoggerFactory.getLogger(NodeDiscoveryService.class);
    private static final long HEARTBEAT_INTERVAL_MILLIS = 5000; // Send heartbeat every 5 seconds
    private static final long PEER_TIMEOUT_MILLIS = 15000;

    private final HashRing hashRing;
    private final NodeApiClient nodeApiClient;
    private final Node currentNode;
    private final NodeConfigProperties nodeConfigProperties;

    // Map to track the last received heartbeat timestamp for each peer (node address -> timestamp)
    private final Map<String, Long> peerLastSeen = new ConcurrentHashMap<>();

    // Scheduler for sending heartbeats and checking peer timeouts
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2); // Two threads: one for sending, one for checking

    public NodeDiscoveryService(HashRing hashRing, NodeApiClient nodeApiClient, NodeConfigProperties nodeConfigProperties){
        this.hashRing = hashRing;
        this.nodeApiClient = nodeApiClient;
        this.nodeConfigProperties = nodeConfigProperties;
        NodeConfigProperties.NodeProperties currentProps = nodeConfigProperties.getNode();
        this.currentNode = new Node(currentProps.getHost() + ":" + currentProps.getPort(), currentProps.getHost(), currentProps.getPort());
        log.info("NodeDiscoveryService initialized for current node: {}", currentNode);

    }

    @PostConstruct
    public void init(){
        // Initial population of peerLastSeen based on static peers from config
        List<String> initialPeers = nodeConfigProperties.getPeers();
        if (initialPeers != null) {
            for (String peerAddress : initialPeers) {
                // Initialize all known peers as "just seen"
                peerLastSeen.put(peerAddress, System.currentTimeMillis());
            }
        }

        // Add this node to the ring initially
        hashRing.addRealNodeToRing(currentNode);
        log.info("Added current node {} to HashRing during NodeDiscoveryService init.", currentNode.getId());


        // Schedule tasks
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, 0, HEARTBEAT_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::checkPeerTimeouts, 0, HEARTBEAT_INTERVAL_MILLIS, TimeUnit.MILLISECONDS); // Check at same interval
        log.info("NodeDiscoveryService started heartbeat sender and peer timeout checker tasks.");
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
        log.info("NodeDiscoveryService scheduler shut down.");
    }

    /**
     * Sends a heartbeat message to all known peers.
     */
    private void sendHeartbeat() {
        HeartbeatRequest heartbeat = new HeartbeatRequest(
                currentNode.getId(), currentNode.getHost(), currentNode.getPort(), System.currentTimeMillis()
        );

        // Get current list of nodes in the ring (which includes self and active peers)
        for (String peerAddress : peerLastSeen.keySet()) { // <--- CHANGE THIS LOOP ITERATION
            try {
                String[] parts = peerAddress.split(":");
                // Reconstruct Node object for sending
                Node peer = new Node(peerAddress, parts[0], Integer.parseInt(parts[1]));

                if (!peer.equals(currentNode)) {
                    nodeApiClient.sendHeartbeat(peer, heartbeat)
                            .doOnError(e -> log.warn("Failed to send heartbeat to {}: {}", peer.getId(), e.getMessage()))
                            .subscribe();
                }
            } catch (Exception e) {
                log.error("Error sending heartbeat to peer {}: {}", peerAddress, e.getMessage());
            }
        }
//        log.debug("Sent heartbeat from {}. Attempted to reach peers: {}", currentNode.getId(), peerLastSeen.keySet().stream().filter(addr -> !addr.equals(currentNode.getAddress())).toList()); // Update logging
    }

    /**
     * Periodically checks for peers that have timed out.
     */
    private void checkPeerTimeouts() {
        long currentTime = System.currentTimeMillis();
        // Iterate over a copy of the keys to avoid ConcurrentModificationException
        List<String> peerAddressesToCheck = peerLastSeen.keySet().stream().toList();

        for (String peerAddress : peerAddressesToCheck) {
            if (peerAddress.equals(currentNode.getAddress())) {
                continue; // Don't check self
            }
            Long lastSeenTime = peerLastSeen.get(peerAddress);
            if (lastSeenTime != null && (currentTime - lastSeenTime) > PEER_TIMEOUT_MILLIS) {
                // Peer has timed out
                log.warn("Peer {} timed out. Last seen {} ms ago. Removing from HashRing.", peerAddress, (currentTime - lastSeenTime));
                // Extract node details from address for removal
                try {
                    String[] parts = peerAddress.split(":");
                    Node downNode = new Node(peerAddress, parts[0], Integer.parseInt(parts[1]));
                    hashRing.removeNode(downNode); // Notify HashRing to remove the node
                    peerLastSeen.remove(peerAddress); // Remove from our tracking map
                    log.info("Node {} removed from active peer list and HashRing.", downNode.getId());
                } catch (Exception e) {
                    log.error("Error creating Node object for timed out peer address {}: {}", peerAddress, e.getMessage());
                }
            }
        }
        log.debug("Checked peer timeouts. Current active peers: {}", peerLastSeen.keySet());
    }


    public void onHeartbeatReceived(HeartbeatRequest request) {
        Node senderNode = new Node(request.getNodeId(), request.getNodeHost(), request.getNodePort());
//        log.debug("Received heartbeat from node: {}", senderNode.getId());

        // Always update last seen time
        peerLastSeen.put(senderNode.getAddress(), System.currentTimeMillis());

        // If this is a new node, or a node that was previously down, add/re-add it to the hash ring
        // HashRing.getNodesInRing() gets distinct real nodes.
        if (!hashRing.getNodesInRing().contains(senderNode)) {
            log.info("New or recovered node detected: {}. Adding to HashRing.", senderNode.getId());
            hashRing.addRealNodeToRing(senderNode);
        }
    }

    public Map<String , Long> getPeerLastSeen(){
        return Collections.unmodifiableMap(peerLastSeen);
    }
}
