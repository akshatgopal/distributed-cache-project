package com.distributed.distributed_cache_project.api.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AdminMetricsResponse {
    private String nodeId;
    private String nodeAddress; // host:port
    private String status;      // e.g., "UP"

    private int localKeyCount;
    private long localMemoryUsageBytes;
    private long totalJVMMemoryBytes;

    private long cacheHitCount;
    private long cacheMissCount;
    private double cacheHitRatio;

    private long putCount;
    private long deleteCount;

    private long lastHeartbeatReceivedMillis; // Timestamp from NodeDiscoveryService

    private List<String> activePeerAddresses; // Addresses of nodes currently seen as UP
}
