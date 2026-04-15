-- init.sql
CREATE DATABASE IF NOT EXISTS minidrive;
USE minidrive;

-- Stores metadata for every uploaded file
CREATE TABLE IF NOT EXISTS file_metadata (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(100),
    checksum VARCHAR(64),
    -- Comma-separated list of storage node URLs that hold this file
    -- e.g. "http://172.20.0.3:8091,http://172.20.0.4:8092"
    storage_nodes VARCHAR(500),
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) DEFAULT 'ACTIVE'
);

-- Tracks which server nodes are alive (master reads this for round-robin)
CREATE TABLE IF NOT EXISTS server_nodes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    node_url VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(20) DEFAULT 'UP',
    last_heartbeat TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Pre-register the two server nodes so master can find them immediately
INSERT INTO server_nodes (node_url, status) VALUES
    ('http://server-node-1:8081', 'UP'),
    ('http://server-node-2:8082', 'UP');