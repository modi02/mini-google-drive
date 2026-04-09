package com.minicloud.storagenode.controller;

import com.minicloud.storagenode.service.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

@RestController
public class FileController {

    private final FileStorageService storageService;

    @Value("${node.id:node-1}")
    private String nodeId;

    public FileController(FileStorageService storageService) {
        this.storageService = storageService;
    }

    // POST /files/upload — master calls this to store a file
    @PostMapping("/files/upload")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "filename", required = false) String filename) {
        try {
            String name = (filename != null && !filename.isBlank()) ? filename : file.getOriginalFilename();
            storageService.save(name, file);
            return ResponseEntity.ok(Map.of(
                    "message", "File stored successfully",
                    "filename", name,
                    "nodeId", nodeId,
                    "size", file.getSize()
            ));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // GET /files/download/{filename} — master calls this to retrieve a file
    @GetMapping("/files/download/{filename}")
    public ResponseEntity<byte[]> download(@PathVariable String filename) {
        try {
            byte[] bytes = storageService.load(filename);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(bytes);
        } catch (FileNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // DELETE /files/delete/{filename} — master calls this to delete a file
    @DeleteMapping("/files/delete/{filename}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable String filename) {
        try {
            storageService.delete(filename);
            return ResponseEntity.ok(Map.of(
                    "message", "File deleted",
                    "filename", filename,
                    "nodeId", nodeId
            ));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // GET /health — master polls this every 10s to check liveness
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "nodeId", nodeId,
                "fileCount", storageService.getFileCount()
        ));
    }
}
