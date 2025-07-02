package com.distributed.distributed_cache_project.network.client;

import com.distributed.distributed_cache_project.api.InternalCacheController;
import com.distributed.distributed_cache_project.core.consistenthashing.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class NodeApiClient {
    private static final Logger log = LoggerFactory.getLogger(NodeApiClient.class);

    private final WebClient webClient;

    // Constructor: Spring injects WebClient.Builder
    public NodeApiClient(WebClient.Builder webClientBuilder) {
        // Configure a base WebClient instance here.
        // You can add default headers, timeouts, etc.
        this.webClient = webClientBuilder
                .baseUrl("") // Base URL set per request below
                // Add timeouts (from application.properties if you added them)
                // .build(); // Build here if you want a single WebClient instance configured once
                // Or build per request if base URL changes frequently
                .build(); // Build here for simplicity for now
    }

    /**
     * Forwards a PUT request to the specified target node's internal API.
     * @param targetNode The node to forward the request to.
     * @param key The key to store.
     * @param value The value to store.
     * @param ttlMillis The time-to-live in milliseconds.
     * @return A Mono<Void> indicating completion or error.
     */
    public Mono<Void> forwardPut(Node targetNode, String key, Object value, long ttlMillis) {
        String url = String.format("http://%s:%d/internal/cache/%s", targetNode.getHost(), targetNode.getPort(), key);
        log.info("Url here is -> '{}",url);
        log.info("Forwarding PUT request for key '{}' to node: {}", key, targetNode.getId());

        InternalCacheController.InternalCachePutRequest requestBody = new InternalCacheController.InternalCachePutRequest();
        requestBody.setValue(value);
        requestBody.setTtlMillis(ttlMillis);

        return webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse -> {
                    // Handle error responses from the target node
                    log.error("Error forwarding PUT for key '{}' to {}: Status {}", key, targetNode.getId(), clientResponse.statusCode());
                    return clientResponse.bodyToMono(String.class)
                            .flatMap(errorBody -> Mono.error(new RuntimeException("Forwarded PUT failed: " + errorBody)));
                })
                .bodyToMono(Void.class) // Expecting no content or specific success message
                .timeout(Duration.ofMillis(5000)) // Example timeout, ideally from properties
                .doOnError(WebClientRequestException.class, e ->
                        log.error("Network error forwarding PUT for key '{}' to {}: {}", key, targetNode.getId(), e.getMessage()))
                .doOnError(Exception.class, e ->
                        log.error("An unexpected error occurred while forwarding PUT for key '{}' to {}: {}", key, targetNode.getId(), e.getMessage()));
    }

    /**
     * Forwards a GET request to the specified target node's internal API.
     * @param targetNode The node to forward the request to.
     * @param key The key to retrieve.
     * @return A Mono<Object> containing the retrieved value, or Mono.empty() if not found.
     */
    public Mono<Object> forwardGet(Node targetNode, String key) {
        String url = String.format("http://%s:%d/internal/cache/%s", targetNode.getHost(), targetNode.getPort(), key);
        log.info("Forwarding GET request for key '{}' to node: {}", key, targetNode.getId());

        return webClient.get()
                .uri(url)
                .retrieve()
                .onStatus(HttpStatus.NOT_FOUND::equals, clientResponse -> Mono.empty()) // Handle 404 explicitly as empty
                .onStatus(HttpStatusCode::isError, clientResponse -> {
                    log.error("Error forwarding GET for key '{}' to {}: Status {}", key, targetNode.getId(), clientResponse.statusCode());
                    return clientResponse.bodyToMono(String.class)
                            .flatMap(errorBody -> Mono.error(new RuntimeException("Forwarded GET failed: " + errorBody)));
                })
                .bodyToMono(Object.class) // Expecting the value as an Object
                .timeout(Duration.ofMillis(5000)) // Example timeout
                .doOnError(WebClientRequestException.class, e ->
                        log.error("Network error forwarding GET for key '{}' to {}: {}", key, targetNode.getId(), e.getMessage()))
                .doOnError(Exception.class, e ->
                        log.error("An unexpected error occurred while forwarding GET for key '{}' to {}: {}", key, targetNode.getId(), e.getMessage()));
    }

    /**
     * Forwards a DELETE request to the specified target node's internal API.
     * @param targetNode The node to forward the request to.
     * @param key The key to delete.
     * @return A Mono<Void> indicating completion or error.
     */
    public Mono<Void> forwardDelete(Node targetNode, String key) {
        String url = String.format("http://%s:%d/internal/cache/%s", targetNode.getHost(), targetNode.getPort(), key);
        log.info("Forwarding DELETE request for key '{}' to node: {}", key, targetNode.getId());

        return webClient.delete()
                .uri(url)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse -> {
                    log.error("Error forwarding DELETE for key '{}' to {}: Status {}", key, targetNode.getId(), clientResponse.statusCode());
                    return clientResponse.bodyToMono(String.class)
                            .flatMap(errorBody -> Mono.error(new RuntimeException("Forwarded DELETE failed: " + errorBody)));
                })
                .bodyToMono(Void.class) // Expecting no content on success (204)
                .timeout(Duration.ofMillis(5000))
                .doOnError(WebClientRequestException.class, e ->
                        log.error("Network error forwarding DELETE for key '{}' to {}: {}", key, targetNode.getId(), e.getMessage()))
                .doOnError(Exception.class, e ->
                        log.error("An unexpected error occurred while forwarding DELETE for key '{}' to {}: {}", key, targetNode.getId(), e.getMessage()));
    }
}
