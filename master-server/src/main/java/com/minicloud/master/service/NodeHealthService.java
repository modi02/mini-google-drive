package com.minicloud.master.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.minicloud.master.model.StorageNode;

import jakarta.annotation.PostConstruct;

/**
 * Responsible for:
 *  1. Registering all storage nodes from config at startup
 *  2. Pinging each node's /health endpoint every 10 seconds
 *  3. Updating isAlive and fileCount on each StorageNode
 */
@Service
public class NodeHealthService {

    private static final Logger log = LoggerFactory.getLogger(NodeHealthService.class);

    @Value("${storage.nodes}")
    private String storageNodesConfig; // comma-separated URLs

    private final ConsistentHashService hashService;
    private final RestTemplate restTemplate;

    // Shared registry — all services read from this map
    private final Map<String, StorageNode> nodeRegistry = new LinkedHashMap<>();

    public NodeHealthService(ConsistentHashService hashService) {
        this.hashService = hashService;
        this.restTemplate = new RestTemplate();
    }

    @PostConstruct
    public void init() {
        String[] urls = storageNodesConfig.split(",");
        for (int i = 0; i < urls.length; i++) {
            String url = urls[i].trim();
            String nodeId = "node-" + (i + 1);
            StorageNode node = new StorageNode(nodeId, url);
            nodeRegistry.put(nodeId, node);
            hashService.addNode(node);
            log.info("Registered storage node: {} at {}", nodeId, url);
        }
        // an immediate health check so nodes are marked alive before first request
        checkHealth();
    }

    @Scheduled(fixedDelayString = "${health.check.interval.ms:10000}")
    public void checkHealth() {
        for (StorageNode node : nodeRegistry.values()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.getForObject(
                        node.getUrl() + "/health", Map.class);

                if (response != null && "UP".equals(response.get("status"))) {
                    node.setAlive(true);
                    Object fc = response.get("fileCount");
                    node.setFileCount(fc instanceof Number ? ((Number) fc).intValue() : 0);
                    log.debug("Node {} is UP (files: {})", node.getNodeId(), node.getFileCount());
                } else {
                    node.setAlive(false);
                    log.warn("Node {} returned unexpected health response", node.getNodeId());
                }
            } catch (Exception e) {
                node.setAlive(false);
                log.warn("Node {} is DOWN: {}", node.getNodeId(), e.getMessage());
            }
        }
    }

    public Collection<StorageNode> getAllNodes() {
        return nodeRegistry.values();
    }

    public StorageNode getNode(String nodeId) {
        return nodeRegistry.get(nodeId);
    }
}
