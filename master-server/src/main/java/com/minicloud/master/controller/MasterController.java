package com.minicloud.master.controller;

import com.minicloud.master.model.StorageNode;
import com.minicloud.master.service.MasterService;
import com.minicloud.master.service.NodeHealthService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(origins = "*") // allow dashboard to call from browser
public class MasterController {

    private final MasterService masterService;
    private final NodeHealthService healthService;

    public MasterController(MasterService masterService, NodeHealthService healthService) {
        this.masterService = masterService;
        this.healthService = healthService;
    }

    // ─── POST /files/upload ───────────────────────────────────────
    @PostMapping("/files/upload")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        try {
            Map<String, Object> result = masterService.uploadFile(file);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ─── GET /files/download/{filename} ──────────────────────────
    @GetMapping("/files/download/{filename}")
    public ResponseEntity<byte[]> download(@PathVariable String filename) {
        try {
            byte[] bytes = masterService.downloadFile(filename);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(bytes);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    // ─── DELETE /files/delete/{filename} ─────────────────────────
    @DeleteMapping("/files/delete/{filename}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String filename) {
        try {
            return ResponseEntity.ok(masterService.deleteFile(filename));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ─── GET /files/list ──────────────────────────────────────────
    @GetMapping("/files/list")
    public ResponseEntity<List<Map<String, Object>>> list() {
        return ResponseEntity.ok(masterService.listFiles());
    }

    // ─── GET /nodes/status ───────────────────────────────────────
    @GetMapping("/nodes/status")
    public ResponseEntity<List<Map<String, Object>>> nodeStatus() {
        List<Map<String, Object>> status = healthService.getAllNodes().stream()
                .map(node -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("nodeId", node.getNodeId());
                    m.put("url", node.getUrl());
                    m.put("alive", node.isAlive());
                    m.put("fileCount", node.getFileCount());
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(status);
    }
}
