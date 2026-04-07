# Distributed Cache Project

A scalable, in-memory distributed cache system built with Spring Boot and Java 21. Uses consistent hashing for data distribution and replication for fault tolerance.

---

## Features

- **Consistent Hashing** — Automatic data partitioning across nodes with MurmurHash3
- **Replication** — Configurable replication factor for fault tolerance
- **LRU Eviction** — Least Recently Used eviction when cache reaches capacity
- **TTL Support** — Time-based expiration with automatic cleanup
- **REST API** — Simple GET, PUT, DELETE operations
- **Non-blocking** — Built with Spring WebFlux for high throughput

---

## Architecture Overview

```
Client Request
      │
      ▼
┌─────────────────────────────────────┐
│         CacheController             │
│      (Public REST API)              │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│         CacheService                │
│   (Routing & Replication Logic)    │
└──────────────┬──────────────────────┘
               │
       ┌───────┴───────┐
       ▼               ▼
┌─────────────┐  ┌──────────────────┐
│ HashRing    │  │ NodeApiClient    │
│ (Who has    │  │ (Forward to      │
│  which key) │  │  other nodes)    │
└─────────────┘  └──────────────────┘
       │
       ▼
┌─────────────────────────────────────┐
│        LocalCache                   │
│   (In-memory storage with           │
│    LRU + TTL)                       │
└─────────────────────────────────────┘
```

---

## Quick Start

### Prerequisites

- Java 21
- Maven 3.6+

### Running a Single Node

```bash
cd distributed-cache-project

# Build
./mvnw clean package -DskipTests

# Run
./mvnw spring-boot:run
```

The node starts on `http://localhost:8080`

---

## Running a Cluster (3 Nodes)

You need to run 3 separate instances with different ports.

### Step 1: Build the project

```bash
./mvnw clean package -DskipTests
```

### Step 2: Run Node 1 (Port 8080)

```bash
# Edit application.properties to set:
# cache.node.port=8080
# cache.peers=localhost:8080,localhost:8081,localhost:8082

./mvnw spring-boot:run
```

### Step 3: Run Node 2 (Port 8081)

```bash
# Edit application.properties to set:
# cache.node.port=8081
# cache.peers=localhost:8080,localhost:8081,localhost:8082

./mvnw spring-boot:run
```

### Step 4: Run Node 3 (Port 8082)

```bash
# Edit application.properties to set:
# cache.node.port=8082
# cache.peers=localhost:8080,localhost:8081,localhost:8082

./mvnw spring-boot:run
```

---

## Configuration

All configuration is in `src/main/resources/application.properties`:

```properties
# Node Identity
cache.node.id=cache-node-1
cache.node.host=localhost
cache.node.port=8080

# All nodes in cluster (including self)
cache.peers=localhost:8080,localhost:8081,localhost:8082

# Replication factor (1 = no replication, 2 = 1 primary + 1 replica)
cache.replication.factor=2

# Max entries per node
cache.capacity.max-entries=1000

# Network timeouts (milliseconds)
cache.network.connect-timeout-millis=5000
cache.network.read-timeout-millis=10000
```

---

## API Endpoints

### Public API (External Clients)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/cache/{key}` | Get value by key |
| POST | `/cache/{key}` | Set value (body below) |
| DELETE | `/cache/{key}` | Delete key |
| GET | `/cache/` | Get all cache entries |

### POST Body Format

```json
{
  "value": "your-value",
  "ttlMillis": 60000
}
```

**Example:**

```bash
# Set a key
curl -X POST http://localhost:8080/cache/user:1 \
  -H "Content-Type: application/json" \
  -d '{"value": "Akshat", "ttlMillis": 60000}'

# Get a key
curl http://localhost:8080/cache/user:1

# Delete a key
curl -X DELETE http://localhost:8080/cache/user:1
```

### Internal API (Node-to-Node)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/internal/cache/{key}` | Internal get |
| POST | `/internal/cache/{key}` | Internal put |
| DELETE | `/internal/cache/{key}` | Internal delete |
| POST | `/internal/cache/heartbeat` | Heartbeat |

*Internal APIs are used for replication and node communication.*

---

## How It Works

### Consistent Hashing

When you store a key, the system uses MurmurHash3 to calculate which node owns that key. The HashRing distributes keys across nodes evenly using virtual nodes (100 per physical node).

```
Key "user:1" → Hash → 50000 → Ring position → Node B (primary)
                                        → Node C (replica)
```

### Replication

With `replication.factor=2`:
- Primary node stores the key
- Replica node gets an async copy

If the primary node fails, the data is still available on the replica.

### LRU + TTL

- **LRU:** When cache reaches `max-entries`, least recently used items are evicted
- **TTL:** Items expire after `ttlMillis`. A background task runs every 5 minutes to clean expired entries

---

## Testing the Cluster

### Start 3 terminals:

**Terminal 1:**
```bash
# Node 1: application.properties has port 8080
./mvnw spring-boot:run
```

**Terminal 2:**
```bash
# Node 2: application.properties has port 8081
./mvnw spring-boot:run
```

**Terminal 3:**
```bash
# Node 3: application.properties has port 8082
./mvnw spring-boot:run
```

### Test:

```bash
# Put a value to Node 1 (will be distributed based on key hash)
curl -X POST http://localhost:8080/cache/mykey \
  -H "Content-Type: application/json" \
  -d '{"value": "hello", "ttlMillis": 60000}'

# Get from Node 2 (will forward to correct node)
curl http://localhost:8080/cache/mykey
```

---

## Project Structure

```
src/
├── main/
│   ├── java/com/distributed/distributed_cache_project/
│   │   ├── api/
│   │   │   ├── CacheController.java      # Public API
│   │   │   └── InternalCacheController.java  # Internal API
│   │   ├── config/
│   │   │   └── NodeConfigProperties.java # Configuration binding
│   │   ├── core/
│   │   │   ├── cache/
│   │   │   │   ├── LocalCache.java       # LRU + TTL storage
│   │   │   │   └── CacheEntry.java       # Cache entry with TTL
│   │   │   └── consistenthashing/
│   │   │       ├── HashRing.java         # Consistent hash ring
│   │   │       └── Node.java             # Node representation
│   │   ├── network/
│   │   │   └── client/
│   │   │       └── NodeApiClient.java    # HTTP client for inter-node calls
│   │   └── service/
│   │       └── CacheService.java         # Main business logic
│   └── resources/
│       └── application.properties        # Configuration
```

---

## API Response Examples

### Successful GET

```json
{
  "value": "Akshat"
}
```

### Successful PUT

```json
{
  "status": "Key 'user:1' stored successfully."
}
```

### Key Not Found

```json
{
  "error": "Not Found"
}
```

---

## Troubleshooting

### Node not joining the cluster

Check `application.properties`:
- Are all peers listed in `cache.peers`?
- Is `cache.node.port` correct?

### All nodes show empty on GET

- Check logs: Are requests being forwarded correctly?
- Verify replication factor is set

### Keys not found

- Check if TTL expired
- Verify the key hash is being calculated the same on all nodes

---

## Future Improvements

- Dynamic node discovery (instead of static list)
- Heartbeat-based failure detection
- Data redistribution on node failure
- Metrics dashboard
- Redis/Memcached protocol support

---

## License

MIT License