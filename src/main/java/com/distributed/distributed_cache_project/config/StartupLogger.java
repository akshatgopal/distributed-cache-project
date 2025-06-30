package com.distributed.distributed_cache_project.config;

import com.distributed.distributed_cache_project.core.consistenthashing.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class StartupLogger implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupLogger.class);
    private final NodeConfigProperties nodeConfigProperties;

    public StartupLogger(NodeConfigProperties nodeConfigProperties){
        this.nodeConfigProperties = nodeConfigProperties;
    }
    @Override
    public void run(String... args) throws Exception {
        log.info("--- Node Configuration Loaded ---");

        // Log current node's properties
        NodeConfigProperties.NodeProperties currentNodeProps = nodeConfigProperties.getNode();
        if (currentNodeProps != null) {
            log.info("Current Node ID: {}", currentNodeProps.getId());
            log.info("Current Node Host: {}", currentNodeProps.getHost());
            log.info("Current Node Port: {}", currentNodeProps.getPort());

            // Create a Node object for the current node as well, for verification
            Node currentNode = new Node(currentNodeProps.getId(), currentNodeProps.getHost(), currentNodeProps.getPort());
            log.info("Constructed Current Node Object: {}", currentNode);

        } else {
            log.warn("No 'cache.node' properties found in configuration!");
        }

        // Log peer nodes
        if (nodeConfigProperties.getPeers() != null && !nodeConfigProperties.getPeers().isEmpty()) {
            log.info("Configured Peer Nodes (Raw): {}", nodeConfigProperties.getPeers());
            log.info("--- Parsing Peer Nodes ---");
            nodeConfigProperties.getPeers().forEach(peer -> {
                try {
                    String[] parts = peer.split(":");
                    if (parts.length == 2) {
                        String host = parts[0];
                        int port = Integer.parseInt(parts[1]);
                        // For peers, we don't necessarily have their 'id' from the raw string.
                        // We might assign a synthetic ID or just use host:port as ID temporarily.
                        // For now, let's just log the parsed host/port.
                        log.info("  Parsed Peer: Host={}, Port={}", host, port);
                    } else {
                        log.warn("  Invalid peer format: {}. Expected host:port", peer);
                    }
                } catch (NumberFormatException e) {
                    log.error("  Invalid port number in peer: {}", peer, e);
                }
            });
        } else {
            log.warn("No 'cache.peers' defined in configuration!");
        }

        log.info("--- Configuration Load Complete ---");
    }
}
