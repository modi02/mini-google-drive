package com.minicloud.master.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.minicloud.master.model.FileMetadata;
import com.minicloud.master.model.StorageNode;

@Service
public class MasterService {

    private static final Logger log = LoggerFactory.getLogger(MasterService.class);

    @Value("${replication.factor:2}")
    private int replicationFactor;

    private final ConsistentHashService hashService;
    private final NodeHealthService healthService;
    private final RestTemplate restTemplate;

    // In-memory metadata store: filename -> FileMetadata
    private final ConcurrentHashMap<String, FileMetadata> metadataStore = new ConcurrentHashMap<>();

    public MasterService(ConsistentHashService hashService, NodeHealthService healthService) {
        this.hashService = hashService;
        this.healthService = healthService;
        this.restTemplate = new RestTemplate();
    }

    // ─────────────────────────── UPLOAD ───────────────────────────

    /**
     * 1. Use consistent hashing to pick replicationFactor alive nodes
     * 2. Forward the file to each selected node
     * 3. Save metadata
     */
    public Map<String, Object> uploadFile(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Filename cannot be empty");
        }

        List<StorageNode> targets = hashService.selectNodes(filename, replicationFactor);
        if (targets.isEmpty()) {
            throw new IllegalStateException("No alive storage nodes available");
        }

        byte[] fileBytes = file.getBytes();
        List<String> successfulNodes = new ArrayList<>();

        for (StorageNode node : targets) {
            try {
                forwardFileToNode(node, filename, fileBytes, file.getContentType());
                successfulNodes.add(node.getNodeId());
                log.info("Replicated '{}' to {}", filename, node.getNodeId());
            } catch (Exception e) {
                log.error("Failed to replicate '{}' to {}: {}", filename, node.getNodeId(), e.getMessage());
            }
        }

        if (successfulNodes.isEmpty()) {
            throw new IllegalStateException("Upload failed — could not write to any storage node");
        }

        FileMetadata metadata = new FileMetadata(filename, file.getSize(), successfulNodes);
        metadataStore.put(filename, metadata);

        return Map.of(
                "filename", filename,
                "size", file.getSize(),
                "replicatedTo", successfulNodes,
                "replicationFactor", successfulNodes.size()
        );
    }

    private void forwardFileToNode(StorageNode node, String filename, byte[] bytes, String contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() { return filename; }
        };
        body.add("file", resource);
        body.add("filename", filename);

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
        restTemplate.postForEntity(node.getUrl() + "/files/upload", request, Map.class);
    }

    // ─────────────────────────── DOWNLOAD ───────────────────────────

    /**
     * 1. Look up metadata to find replica nodes
     * 2. Try first alive replica, fall back to next if down
     */
    public byte[] downloadFile(String filename) throws IOException {
        FileMetadata metadata = metadataStore.get(filename);
        if (metadata == null) {
            throw new IllegalArgumentException("File not found in metadata: " + filename);
        }

        for (String nodeId : metadata.getReplicaNodeIds()) {
            StorageNode node = healthService.getNode(nodeId);
            if (node == null || !node.isAlive()) {
                log.warn("Skipping dead node {} for download of '{}'", nodeId, filename);
                continue;
            }
            try {
                ResponseEntity<byte[]> response = restTemplate.getForEntity(
                        node.getUrl() + "/files/download/" + filename, byte[].class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    log.info("Downloaded '{}' from {}", filename, nodeId);
                    return response.getBody();
                }
            } catch (Exception e) {
                log.warn("Failed to download '{}' from {}: {}", filename, nodeId, e.getMessage());
            }
        }
        throw new IllegalStateException("All replicas unavailable for file: " + filename);
    }

    // ─────────────────────────── DELETE ───────────────────────────

    public Map<String, Object> deleteFile(String filename) {
        FileMetadata metadata = metadataStore.get(filename);
        if (metadata == null) {
            throw new IllegalArgumentException("File not found: " + filename);
        }

        List<String> deletedFrom = new ArrayList<>();
        for (String nodeId : metadata.getReplicaNodeIds()) {
            StorageNode node = healthService.getNode(nodeId);
            if (node == null) continue;
            try {
                restTemplate.delete(node.getUrl() + "/files/delete/" + filename);
                deletedFrom.add(nodeId);
            } catch (Exception e) {
                log.warn("Could not delete '{}' from {}: {}", filename, nodeId, e.getMessage());
            }
        }

        metadataStore.remove(filename);
        return Map.of("message", "File deleted", "filename", filename, "deletedFrom", deletedFrom);
    }

    // ─────────────────────────── LIST ───────────────────────────

    public List<Map<String, Object>> listFiles() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (FileMetadata m : metadataStore.values()) {
            result.add(Map.of(
                    "filename", m.getFilename(),
                    "size", m.getSize(),
                    "replicaNodes", m.getReplicaNodeIds(),
                    "uploadedAt", m.getUploadedAt().toString()
            ));
        }
        return result;
    }
}
