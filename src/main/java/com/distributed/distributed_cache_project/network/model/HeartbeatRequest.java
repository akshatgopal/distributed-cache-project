package com.distributed.distributed_cache_project.network.model;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HeartbeatRequest {
    private String nodeId;
    private String nodeHost;
    private int nodePort;
    private long timestamp;
}
