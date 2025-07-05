package com.distributed.distributed_cache_project.api;


import com.distributed.distributed_cache_project.api.model.AdminMetricsResponse;
import com.distributed.distributed_cache_project.config.NodeConfigProperties;
import com.distributed.distributed_cache_project.core.cache.LocalCache;
import com.distributed.distributed_cache_project.core.consistenthashing.Node;
import com.distributed.distributed_cache_project.network.discovery.NodeDiscoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final LocalCache localCache;
    private final NodeDiscoveryService nodeDiscoveryService;
    private final Node currentNode; // To provide this node's own info

    public AdminController(LocalCache localCache,
                           NodeDiscoveryService nodeDiscoveryService,
                           NodeConfigProperties nodeConfigProperties) { // Inject config to get current node
        this.localCache = localCache;
        this.nodeDiscoveryService = nodeDiscoveryService;
        NodeConfigProperties.NodeProperties currentProps = nodeConfigProperties.getNode();
        this.currentNode = new Node(currentProps.getHost() + ":" + currentProps.getPort(), currentProps.getHost(), currentProps.getPort());
        log.info("AdminController initialized for node: {}", currentNode.getId());
    }

    /**
     * Provides comprehensive metrics for the current cache node and its view of the cluster.
     * @return AdminMetricsResponse containing all relevant stats.
     */
    @GetMapping("/stats")
    public ResponseEntity<AdminMetricsResponse> getAdminStats() {
        AdminMetricsResponse response = new AdminMetricsResponse();

        // Node Identification
        response.setNodeId(currentNode.getId());
        response.setNodeAddress(currentNode.getAddress());
        response.setStatus("UP"); // Assumed UP if this endpoint is reachable

        // Local Cache Metrics
        response.setLocalKeyCount(localCache.size());
        response.setLocalMemoryUsageBytes(localCache.getUsedMemoryBytes());
        response.setTotalJVMMemoryBytes(localCache.getTotalMemoryBytes());

        // Cache Hit/Miss/Put/Delete Counts
        response.setCacheHitCount(localCache.getHitCount());
        response.setCacheMissCount(localCache.getMissCount());
        response.setCacheHitRatio(localCache.getHitRatio());
        response.setPutCount(localCache.getPutCount());
        response.setDeleteCount(localCache.getDeleteCount());

        // Node Discovery / Cluster View Metrics
        // For simplicity, NodeDiscoveryService could expose a method like getActivePeersMap()
        // or getPeerLastSeenMap() if you want more detail.
        // For now, let's list the actively seen peers.
        response.setActivePeerAddresses(nodeDiscoveryService.getPeerLastSeen().keySet().stream() // Assuming NodeDiscoveryService exposes this map
                .filter(peerAddress -> !peerAddress.equals(currentNode.getAddress())) // Exclude self
                .collect(Collectors.toList()));

        // This is a placeholder; you'd need NodeDiscoveryService to expose this data.
        // For simplicity, you can just return the current timestamp if not directly tracking individual last heartbeats.
        response.setLastHeartbeatReceivedMillis(System.currentTimeMillis());

        log.debug("AdminController: Returning stats for node {}: Keys={}, Hits={}",
                currentNode.getId(), response.getLocalKeyCount(), response.getCacheHitCount());

        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
