# Mini Google Drive — Distributed File Storage System

A distributed file storage system inspired by how **Amazon S3**, **HDFS**, and **Dropbox** work internally. Built with Java + Spring Boot, running across isolated Docker containers with consistent hashing and automatic replication.

---

## What It Does

- Files are stored across **multiple Docker containers** (simulating real distributed nodes)
- Every file is **automatically replicated to 2 nodes** for fault tolerance
- **Consistent hashing** deterministically routes files to the correct nodes
- A **live dashboard** shows real-time node health and file metadata

---

## System Architecture

```
 CLIENT (Browser / Postman)
        │
        │ REST HTTP :8080
        ▼
 MASTER SERVER (:8080)
   ├── Consistent Hash Ring
   ├── File Metadata Store
   ├── Node Health Checks (every 10s)
   └── Live Dashboard
        │
        ├──────────────────────┐
        ▼                      ▼
 Storage Node 1 (:8081)    Storage Node 2 (:8082)
   IP: 172.20.0.3               IP: 172.20.0.4
   /app/storage-data             /app/storage-data
```

### Core Rules
- **Rule 1:** Client never talks directly to storage nodes — only through Master
- **Rule 2:** Every file is stored on exactly 2 nodes (replication factor = 2)
- **Rule 3:** If one node dies, the other serves the file — client sees no error

---

## Key Algorithms & Concepts

### Consistent Hashing
- `hash("filename")` → position on a ring (0 → 2³²)
- Walk clockwise → first 2 distinct nodes = replica nodes
- Each physical node gets **150 virtual nodes** for even distribution
- **Deterministic:** same filename always maps to the same 2 nodes
- **Scalable:** adding a new node only moves a fraction of files
- Same algorithm used by **Amazon DynamoDB**, **Apache Cassandra**, and **Akamai CDN**

### File Upload Flow
1. **Client Uploads** — Browser sends `POST /files/upload` with file to Master `:8080`
2. **Hash & Select** — Master runs `hash("filename")` on the ring → picks 2 alive nodes
3. **Replicate** — Master forwards file bytes to both Node-1 and Node-2 via HTTP
4. **Save Metadata** — Master stores `{"report.pdf" → ["node-1", "node-2"]}` in memory
5. **Respond** — Client receives `{filename, size, replicatedTo: ["node-1", "node-2"]}`

> **Key insight:** Client never knows which nodes store its file. The master handles all routing transparently.

### Fault Tolerance
| Scenario | Behavior |
|----------|----------|
| Node health check | Master pings `GET /health` on each node every 10 seconds |
| Node failure detected | `isAlive = false` — node is skipped in routing |
| File download with dead node | Master tries replicas in order, skips dead nodes — client sees no error |

---

## Technology Stack

| Layer | Technology | Details |
|-------|-----------|---------|
| Language | Java 17 | Records, sealed classes, text blocks |
| Framework | Spring Boot 3.2 | Auto-config, embedded Tomcat, `@Scheduled` for health checks, `RestTemplate` for inter-node HTTP |
| Algorithm | Consistent Hashing | MD5 hash ring with 150 virtual nodes per physical node |
| Infrastructure | Docker + Compose | 3 isolated containers on a private bridge network, each with its own IP and filesystem |
| Build Tool | Maven | Packages each Spring Boot project into a fat JAR |
| Frontend | Vanilla JS + HTML | No React, no framework — pure HTML/CSS/JS served as static files from the master JAR |

### Docker Network
```
Docker Bridge Network — 172.20.0.0/24

master-server     → 172.20.0.2 :8080
storage-node-1    → 172.20.0.3 :8081
storage-node-2    → 172.20.0.4 :8081
```

---

## Getting Started

### Prerequisites
- Java 17+
- Docker & Docker Compose
- Maven

### Run the System

```bash
# Clone the repository
git clone <your-repo-url>
cd mini-google-drive

# Build all JARs
mvn clean package

# Start all containers
docker-compose up --build

# Access the live dashboard
open http://localhost:8080
```

### API Endpoints

```bash
# Upload a file
POST http://localhost:8080/files/upload

# Download a file
GET http://localhost:8080/files/download/{filename}

# Node health check
GET http://localhost:{node-port}/health
```

---

## Fault Tolerance Demo

```bash
# 1. Open dashboard — both nodes should be green
open http://localhost:8080

# 2. Upload any file
curl -F "file=@report.pdf" http://localhost:8080/files/upload

# 3. Kill node 1
docker stop storage-node-1

# 4. Watch the node turn RED on dashboard (within 10s)

# 5. Download the same file — still works from node-2!
curl http://localhost:8080/files/download/report.pdf -o downloaded.pdf

# 6. Bring node back
docker start storage-node-1
# Node turns green again on dashboard
```

---

## CAP Theorem

> *"A distributed system can only guarantee 2 of these 3 properties at the same time"*

**This system is AP (Availability + Partition Tolerance):**
- We serve files from healthy replicas even when a node is partitioned
- We do **not** block waiting for consistency

| System | Type |
|--------|------|
| **Mini Google Drive** | AP |
| Cassandra | AP |
| HBase | CP |
| MongoDB | CP |

---

## Known Limitations

| Limitation | Current State | Future Fix |
|-----------|--------------|------------|
| **In-Memory Metadata** | Master stores filename→nodes in a `HashMap`. Restarting the master loses all file location data. | Persist metadata to PostgreSQL, Redis, or Zookeeper |
| **Master is SPOF** | If the master goes down, no uploads or downloads work at all. | Master replication with leader election using Raft or Zookeeper |
| **No File Chunking** | Large files are stored whole. Real HDFS splits files into 128MB blocks. | Split files into blocks at upload, reassemble on download |
| **No Authentication** | Anyone who can reach port 8080 can upload, download, or delete any file. | JWT-based auth, user sessions, per-file access control lists |

---

## Inspiration

This project is inspired by the internal architecture of:
- **Amazon S3** — object storage with consistent hashing
- **HDFS (Hadoop)** — distributed file system with block replication
- **Dropbox** — file sync with replication and fault tolerance
- **Amazon DynamoDB** — consistent hashing with virtual nodes

---

## License

MIT License — feel free to use this for learning and educational purposes.
