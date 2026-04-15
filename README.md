# Mini Google Drive — Distributed File Storage System

> A fully distributed file storage system built for the Distributed Systems course at SVNIT Surat.
> Implements leader election, round-robin load balancing, data replication, shared metadata, and automatic failover.

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green)
![Docker](https://img.shields.io/badge/Docker-Compose-blue)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue)
![Nginx](https://img.shields.io/badge/Nginx-1.25-009639)

---

## Architecture

```
Client (Browser / curl)
         |
         ▼
   ┌─────────────┐
   │    Nginx    │  ← Reverse proxy. Single entry point. Auto-failover.
   │   :80       │
   └──────┬──────┘
          │ routes to active master
   ┌──────┴──────────────────────┐
   │         Layer 1             │  LEADER ELECTION
   │  ┌──────────┐ ┌──────────┐  │
   │  │ Master-1 │ │ Master-2 │  │  Primary + Backup
   │  │  :8080   │ │  :8090   │  │  Promotes in 5s on failure
   │  └──────────┘ └──────────┘  │
   └──────────────┬──────────────┘
                  │ round-robin
   ┌──────────────┴──────────────┐
   │         Layer 2             │  ROUND ROBIN LOAD BALANCING
   │  ┌──────────┐ ┌──────────┐  │
   │  │ Server-1 │ │ Server-2 │  │  File logic + MySQL access
   │  │  :8081   │ │  :8082   │  │
   │  └──────────┘ └──────────┘  │
   └──────────────┬──────────────┘
                  │ replication factor = 2
   ┌──────────────┴──────────────┐
   │         Layer 3             │  FILE STORAGE
   │  ┌──────────┐ ┌──────────┐  │
   │  │Storage-1 │ │Storage-2 │  │  Actual file bytes
   │  │  :8091   │ │  :8092   │  │
   │  └──────────┘ └──────────┘  │
   └─────────────────────────────┘
          ↕
   ┌─────────────┐
   │    MySQL    │  ← Shared metadata. All server nodes read/write same DB.
   │   :3306     │
   └─────────────┘
```

---

## Project Structure

```
mini-google-drive/
├── master-server/                          # Spring Boot — Master nodes
│   ├── src/main/java/com/minicloud/master/
│   │   ├── controller/
│   │   │   └── MasterController.java       # REST endpoints, leader check, round-robin routing
│   │   ├── service/
│   │   │   ├── LeaderElectionService.java  # Primary/backup election logic
│   │   │   ├── ServerNodeRouter.java       # Round-robin across server nodes
│   │   │   ├── ConsistentHashService.java  # Hash ring for storage node selection
│   │   │   ├── MasterService.java          # Core master logic
│   │   │   └── NodeHealthService.java      # Storage node health checks
│   │   └── model/
│   │       ├── FileMetadata.java
│   │       └── StorageNode.java
│   ├── src/main/resources/
│   │   ├── static/
│   │   │   ├── dashboard.html              # Live monitoring dashboard
│   │   │   └── index.html                  # File upload/download web UI
│   │   └── application.properties
│   └── Dockerfile
│
├── server-node/                            # Spring Boot — NEW in Phase 2
│   ├── src/main/java/com/minidrive/servernode/
│   │   ├── FileController.java             # REST: /upload /download /files /health
│   │   ├── FileService.java                # Upload with replication, download with fallback
│   │   ├── FileMetadata.java               # JPA entity → file_metadata table
│   │   ├── FileMetadataRepository.java     # Spring Data JPA repository
│   │   ├── StorageNodeClient.java          # HTTP client for storage nodes
│   │   └── ServerNodeApplication.java      # Main class
│   ├── src/main/resources/
│   │   └── application.properties
│   └── Dockerfile
│
├── storage-node/                           # Spring Boot — File storage (Phase 1)
│   ├── src/main/java/com/minicloud/storagenode/
│   │   ├── controller/FileController.java  # /files/upload /files/download /health
│   │   └── service/FileStorageService.java
│   └── Dockerfile
│
├── docker-compose.yml                      # All 9 containers
├── nginx.conf                              # Reverse proxy config
├── init.sql                                # MySQL table creation
└── README.md
```

---

## Quick Start

### Prerequisites
- Docker Desktop running
- Git

### Run Everything

```bash
git clone https://github.com/modi02/mini-google-drive
cd mini-google-drive
docker-compose build --no-cache
docker-compose up
```

Wait ~60 seconds for all 9 containers to be healthy.

### Open in Browser

| URL | Description |
|-----|-------------|
| `http://localhost/` | File upload/download web UI |
| `http://localhost/dashboard.html` | Live monitoring dashboard |

---

## API Reference

All requests go through Nginx at `http://localhost` (port 80).

### Master Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/master/health` | Leader status, peer alive status |
| `GET` | `/master/status` | Full cluster: all nodes, alive lists, leader info |
| `POST` | `/master/upload` | Upload file (multipart/form-data, field: `file`) |
| `GET` | `/master/download/{fileName}` | Download file by name |
| `GET` | `/master/files` | List all files from MySQL |

### Example curl Commands

```bash
# Upload
echo "hello world" > test.txt
curl -X POST http://localhost/master/upload -F file=@test.txt

# Download
curl -O http://localhost/master/download/test.txt

# List files
curl http://localhost/master/files

# Check cluster status
curl http://localhost/master/status

# Check individual masters
curl http://localhost:8080/master/health   # master-1
curl http://localhost:8090/master/health   # master-2
```

---

## Docker Services

| Container | Image | Ports | IP |
|-----------|-------|-------|----|
| nginx | nginx:1.25-alpine | 80:80 | 172.20.0.8 |
| master-1 | build: ./master-server | 8080:8080 | 172.20.0.2 |
| master-2 | build: ./master-server | 8090:8080 | 172.20.0.9 |
| server-node-1 | build: ./server-node | 8081:8081 | 172.20.0.5 |
| server-node-2 | build: ./server-node | 8082:8082 | 172.20.0.6 |
| storage-node-1 | build: ./storage-node | 8091:8091 | 172.20.0.3 |
| storage-node-2 | build: ./storage-node | 8092:8092 | 172.20.0.4 |
| mysql | mysql:8.0 | 3306:3306 | 172.20.0.10 |

---

## Fault Tolerance Demo

### 1. Leader Election — Kill Primary Master

```bash
# Check current leader
curl http://localhost:8080/master/health
# → {"isLeader":true, "status":"UP"}

# Kill primary
docker kill master-1

# Wait 6 seconds — backup promotes itself
curl http://localhost:8090/master/health
# → {"isLeader":true, "status":"UP"}  ← backup is now leader!

# Bring primary back — it becomes backup
docker start master-1
curl http://localhost:8080/master/health
# → {"isLeader":false, "status":"UP"}  ← back as backup
```

### 2. Storage Node Fault Tolerance

```bash
# Upload a file
echo "test data" > test.txt
curl -X POST http://localhost/master/upload -F file=@test.txt

# Kill one storage node
docker stop storage-node-1

# Download still works — falls back to storage-node-2
curl -O http://localhost/master/download/test.txt
cat test.txt  # → test data
```

### 3. Round Robin Load Balancing

```bash
# Upload multiple files and watch docker logs
# Requests alternate between server-node-1 and server-node-2
docker logs master-1 2>&1 | grep "Round robin selected"
```

---

## Distributed Computing Concepts

| Concept | Implementation |
|---------|----------------|
| **Consistent Hashing** | Storage node selection — minimizes remapping when nodes change |
| **Data Replication** | Every file stored on all storage nodes (replication factor = 2) |
| **Leader Election** | Simplified Raft — primary/backup masters, promotes in 5s |
| **Fault Tolerance** | Download falls back to replica if storage node is down |
| **Load Balancing** | Round-robin across server nodes using `AtomicInteger` |
| **Shared State** | MySQL as distributed metadata store — all nodes in sync |
| **Health Monitoring** | Periodic pings every 5-10s, automatic alive-list maintenance |
| **Reverse Proxy** | Nginx — single entry point, transparent master failover |
| **CAP Theorem** | AP system — Available + Partition Tolerant |
| **Eventual Consistency** | MySQL metadata may briefly lag under high load |

---

## Database Schema

```sql
-- File metadata (one row per uploaded file)
CREATE TABLE file_metadata (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_name    VARCHAR(255) NOT NULL,
    file_size    BIGINT NOT NULL,
    content_type VARCHAR(100),
    checksum     VARCHAR(64),
    storage_nodes VARCHAR(500),   -- "http://storage-node-1:8091,http://storage-node-2:8092"
    uploaded_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status       VARCHAR(20) DEFAULT 'ACTIVE'
);

-- Server node registry
CREATE TABLE server_nodes (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    node_url       VARCHAR(255) NOT NULL UNIQUE,
    status         VARCHAR(20) DEFAULT 'UP',
    last_heartbeat TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    registered_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## Phase Comparison

| Feature | Phase 1 | Phase 2 |
|---------|---------|---------|
| Master nodes | 1 (SPOF) | 2 (leader election) |
| Metadata storage | In-memory HashMap | Shared MySQL |
| Middle layer | None | Server nodes x2 |
| Entry point | Direct :8080 | Nginx :80 |
| Master failover | Manual | Automatic (5s) |
| Containers | 3 | 9 |
| File persistence across restart
| Multi-node consistency

---

## Known Limitations

- **MySQL SPOF** — MySQL itself has no replication. Production fix: MySQL Galera Cluster or etcd
- **Split-brain window** — 5-second window where both masters may think they are leader. Production fix: full Raft consensus
- **No partial write recovery** — if server node crashes mid-upload, file may be partially stored
- **Synchronous replication** — upload waits for all storage nodes. Slower but consistent

---

**Course:** Distributed Systems — B.Tech CSE, SVNIT Surat
**Academic Year:** 2025-26
