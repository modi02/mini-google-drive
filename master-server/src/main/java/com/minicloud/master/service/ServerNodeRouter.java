package com.minicloud.master.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;

@Service
public class ServerNodeRouter {

    private static final Logger log = LoggerFactory.getLogger(ServerNodeRouter.class);

    @Value("${server.nodes}")
    private String serverNodesConfig;

    private final RestTemplate restTemplate = new RestTemplate();

    // Full list of all server nodes (alive or not)
    private List<String> allServerNodes = new ArrayList<>();

    // Only alive server nodes — round robin picks from this list
    private List<String> aliveServerNodes = new ArrayList<>();

    // AtomicInteger is thread-safe counter for round robin
    private final AtomicInteger counter = new AtomicInteger(0);

    @PostConstruct
    public void init() {
        allServerNodes = Arrays.asList(serverNodesConfig.split(","));
        aliveServerNodes = new ArrayList<>(allServerNodes);
        log.info("Registered server nodes: {}", allServerNodes);
        checkServerNodeHealth(); // immediate check on startup
    }

    // Ping all server nodes every 10 seconds, update alive list
    @Scheduled(fixedDelayString = "${health.check.interval.ms:10000}")
    public void checkServerNodeHealth() {
        List<String> alive = new ArrayList<>();
        for (String nodeUrl : allServerNodes) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.getForObject(
                        nodeUrl.trim() + "/health", Map.class);
                if (response != null && "UP".equals(response.get("status"))) {
                    alive.add(nodeUrl.trim());
                    log.debug("Server node UP: {}", nodeUrl);
                } else {
                    log.warn("Server node unhealthy: {}", nodeUrl);
                }
            } catch (Exception e) {
                log.warn("Server node DOWN: {} — {}", nodeUrl, e.getMessage());
            }
        }
        aliveServerNodes = alive;
        log.info("Alive server nodes: {}", aliveServerNodes);
    }

    // Round robin — pick next alive server node
    // If none alive, throws exception
    public String getNextServerNode() {
        if (aliveServerNodes.isEmpty()) {
            throw new IllegalStateException("No alive server nodes available");
        }

        // counter keeps incrementing, modulo gives us round robin index
        int index = Math.abs(counter.getAndIncrement() % aliveServerNodes.size());
        String selected = aliveServerNodes.get(index);
        log.info("Round robin selected server node: {}", selected);
        return selected;
    }

    public List<String> getAliveServerNodes() {
        return aliveServerNodes;
    }

    public List<String> getAllServerNodes() {
        return allServerNodes;
    }
}