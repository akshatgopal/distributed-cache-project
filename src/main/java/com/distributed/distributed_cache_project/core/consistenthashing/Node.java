package com.distributed.distributed_cache_project.core.consistenthashing;

import lombok.Getter;

import java.util.Objects;

@Getter
public class Node {
    private final String id;
    private final String host;
    private final int port;

    public Node(String id, String host, int port){
        if(id == null || id.trim().isEmpty()){
            throw new IllegalArgumentException("Node ID cannot be null or empty.");
        }
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("Node host cannot be null or empty.");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Node port must be a valid port number (1-65535).");
        }
        this.id = id;
        this.host = host;
        this.port = port;
    }

    public String getAddress() {
        return host + ":" + port;
    }

    @Override
    public boolean equals(Object o){
        if(this == o) return  true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return port == node.port &&
                id.equals(node.id) &&
                host.equals(node.host);
    }

    @Override
    public int hashCode(){
        return Objects.hash(id,host,port);
    }

    @Override
    public String toString(){
        return "Node{" +
                "id='" + id + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                '}';
    }
}
