package com.minicloud.master.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.minicloud.master.service.LeaderElectionService;
import com.minicloud.master.service.NodeHealthService;
import com.minicloud.master.service.ServerNodeRouter;

@RestController
@RequestMapping("/master")
public class MasterController {

    private static final Logger log = LoggerFactory.getLogger(MasterController.class);

    @Autowired
    private LeaderElectionService leaderElection;

    @Autowired
    private ServerNodeRouter serverNodeRouter;

    @Autowired
    private NodeHealthService nodeHealthService;

    private final RestTemplate restTemplate = new RestTemplate();

    // ── MASTER HEALTH (peers call this for leader election) ──
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> masterHealth() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "isLeader", leaderElection.isLeader(),
                "selfUrl", leaderElection.getSelfUrl(),
                "peerAlive", leaderElection.isPeerAlive()
        ));
    }

    // ── UPLOAD — forward to next server node via round robin ──
    @PostMapping("/upload")
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file) {
        if (!leaderElection.isLeader()) {
            // Backup master redirects client to the leader
            return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                    .header("Location", leaderElection.getPeerUrl() + "/master/upload")
                    .body("Not the leader. Redirecting to: " + leaderElection.getPeerUrl());
        }

        try {
            String targetNode = serverNodeRouter.getNextServerNode();
            log.info("Forwarding upload to server node: {}", targetNode);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", fileResource);

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    targetNode + "/upload", request, String.class);

            return ResponseEntity.ok("Uploaded via " + targetNode + " → " + response.getBody());

        } catch (Exception e) {
            log.error("Upload failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Upload failed: " + e.getMessage());
        }
    }

    // ── DOWNLOAD — round robin to server node ──
    @GetMapping("/download/{fileName}")
    public ResponseEntity<byte[]> download(@PathVariable String fileName) {
        if (!leaderElection.isLeader()) {
            return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                    .header("Location", leaderElection.getPeerUrl() + "/master/download/" + fileName)
                    .build();
        }

        try {
            String targetNode = serverNodeRouter.getNextServerNode();
            log.info("Forwarding download to server node: {}", targetNode);

            ResponseEntity<byte[]> response = restTemplate.getForEntity(
                    targetNode + "/download/" + fileName, byte[].class);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", fileName);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(response.getBody());

        } catch (Exception e) {
            log.error("Download failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(("Download failed: " + e.getMessage()).getBytes());
        }
    }

    // ── LIST FILES — ask any alive server node ──
    @GetMapping("/files")
    public ResponseEntity<?> listFiles() {
        try {
            String targetNode = serverNodeRouter.getNextServerNode();
            ResponseEntity<Object> response = restTemplate.getForEntity(
                    targetNode + "/files", Object.class);
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("No server nodes available: " + e.getMessage());
        }
    }

    // ── CLUSTER STATUS — useful for dashboard + demo ──
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> clusterStatus() {
        return ResponseEntity.ok(Map.of(
                "thisNode", leaderElection.getSelfUrl(),
                "isLeader", leaderElection.isLeader(),
                "peerUrl", leaderElection.getPeerUrl(),
                "peerAlive", leaderElection.isPeerAlive(),
                "aliveServerNodes", serverNodeRouter.getAliveServerNodes(),
                "allServerNodes", serverNodeRouter.getAllServerNodes(),
                "storageNodes", nodeHealthService.getAllNodes()
        ));
    }
}