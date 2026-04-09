package com.minicloud.master.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.minicloud.master.model.StorageNode;

/**
 * Consistent Hash Ring implementation with virtual nodes.
 * Each physical StorageNode is placed at VIRTUAL_NODE_COUNT       positions on the ring.
 * A file maps to the first N nodes clockwise from hash(filename) on the ring.
 * This is the same algorithm used by Amazon DynamoDB and Apache Cassandra.
 */
@Service
public class ConsistentHashService {

    private static final int VIRTUAL_NODE_COUNT = 150; // virtual nodes per physical node
    private final TreeMap<Long, StorageNode> ring = new TreeMap<>();

    /*
     * Register a node on the ring at VIRTUAL_NODE_COUNT positions.
     * Call this once per node during startup.
     */
    public synchronized void addNode(StorageNode node) {
        for (int i = 0; i < VIRTUAL_NODE_COUNT; i++) {
            long hash = hash(node.getNodeId() + "-vnode-" + i);
            ring.put(hash, node);
        }
    }

    /**
     * Select `count` distinct alive nodes for a given filename.
     * Walks clockwise from hash(filename), skipping duplicates and dead nodes.
     */
    public synchronized List<StorageNode> selectNodes(String filename, int count) {
        if (ring.isEmpty()) return Collections.emptyList();

        long fileHash = hash(filename);
        List<StorageNode> selected = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // Get tailMap from fileHash position, wrapping around with the full ring
        NavigableMap<Long, StorageNode> tail = ring.tailMap(fileHash, true);
        Iterable<StorageNode> candidates = () -> {
            List<StorageNode> all = new ArrayList<>();
            tail.values().forEach(all::add);
            ring.headMap(fileHash, false).values().forEach(all::add);
            return all.iterator();
        };

        for (StorageNode node : candidates) {
            if (selected.size() >= count) break;
            if (node.isAlive() && !seen.contains(node.getNodeId())) {
                selected.add(node);
                seen.add(node.getNodeId());
            }
        }
        return selected;
    }

    /**
     * MD5-based hash returning a long — gives good distribution across the ring.
     */
    private long hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(key.getBytes(StandardCharsets.UTF_8));
            // Use first 8 bytes as a long
            long hash = 0;
            for (int i = 0; i < 8; i++) {
                hash = (hash << 8) | (bytes[i] & 0xFF);
            }
            return hash;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }

    public int getRingSize() {
        return ring.size();
    }
}
