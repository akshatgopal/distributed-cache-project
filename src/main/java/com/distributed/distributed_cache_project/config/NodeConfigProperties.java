package com.distributed.distributed_cache_project.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "cache")
public class NodeConfigProperties {

    private NodeProperties node;
    // --- Peer Nodes ---
    // List of "host:port" strings for all known peer nodes
    private List<String> peers;
    private ReplicationProperties replication;
    private CacheCapacityProperties capacity;

    @Data
    public static class NodeProperties {
        private String id;
        private String host;
        private int port;
    }

    @Data
    public static class ReplicationProperties {
        private int factor; // The replication factor
    }

    @Data
    public static class CacheCapacityProperties {
        private int maxEntries; // Maximum number of entries
    }

}
