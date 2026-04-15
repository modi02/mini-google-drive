package com.minicloud.master.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;

@Service
public class LeaderElectionService {

    private static final Logger log = LoggerFactory.getLogger(LeaderElectionService.class);

    @Value("${master.self.url}")
    private String selfUrl;

    @Value("${master.peer.url}")
    private String peerUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // Is this master currently the leader?
    private volatile boolean isLeader = false;

    // Is the peer master currently alive?
    private volatile boolean isPeerAlive = false;

    @PostConstruct
    public void init() {
        // master-1 (port 8080) starts as leader
        // master-2 (port 8090) starts as backup
        if (selfUrl.contains("8080")) {
            isLeader = true;
            log.info("Starting as PRIMARY LEADER: {}", selfUrl);
        } else {
            isLeader = false;
            log.info("Starting as BACKUP: {}", selfUrl);
        }
    }

    // Runs every 5 seconds — checks if peer is alive
    @Scheduled(fixedDelayString = "${leader.check.interval.ms:5000}")
    public void checkPeerAndElect() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(
                    peerUrl + "/master/health", Map.class);

            if (response != null && "UP".equals(response.get("status"))) {
                isPeerAlive = true;

                // If peer is alive and claims to be leader, we become backup
                Boolean peerIsLeader = (Boolean) response.get("isLeader");
                if (Boolean.TRUE.equals(peerIsLeader) && isLeader) {
                    // Both think they're leader — lower port wins
                    if (selfUrl.contains("8090")) {
                        isLeader = false;
                        log.info("Peer is leader, stepping down to BACKUP: {}", selfUrl);
                    }
                }
            } else {
                isPeerAlive = false;
            }

        } catch (Exception e) {
            // Peer is unreachable — if we're backup, promote ourselves
            isPeerAlive = false;
            if (!isLeader) {
                isLeader = true;
                log.warn("Peer {} is DOWN — promoting self to LEADER: {}", peerUrl, selfUrl);
            }
        }
    }

    public boolean isLeader() {
        return isLeader;
    }

    public boolean isPeerAlive() {
        return isPeerAlive;
    }

    public String getSelfUrl() {
        return selfUrl;
    }

    public String getPeerUrl() {
        return peerUrl;
    }
}