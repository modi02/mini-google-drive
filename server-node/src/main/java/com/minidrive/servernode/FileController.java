package com.minidrive.servernode;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class FileController {

    @Autowired
    private FileService fileService;

    // This node's own URL (from application.properties / docker-compose env)
    @Value("${node.self.url}")
    private String selfUrl;

    // --- HEALTH CHECK ---
    // Master calls this to know if this server node is alive
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "node", selfUrl
        ));
    }

    // --- UPLOAD ---
    // Master forwards upload requests here
    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("ERROR: No file provided");
        }

        String result = fileService.uploadFile(file);

        if (result.startsWith("ERROR")) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }

        return ResponseEntity.ok(result);
    }

    // --- DOWNLOAD ---
    // Master forwards download requests here
    @GetMapping("/download/{fileName}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String fileName) {
        byte[] fileBytes = fileService.downloadFile(fileName);

        if (fileBytes == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(("File not found: " + fileName).getBytes());
        }

        // Tell the browser/client to treat this as a file download
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", fileName);

        return ResponseEntity.ok()
                .headers(headers)
                .body(fileBytes);
    }

    // --- LIST FILES ---
    // Returns all files stored (reads from shared MySQL)
    @GetMapping("/files")
    public ResponseEntity<List<FileMetadata>> listFiles() {
        return ResponseEntity.ok(fileService.listFiles());
    }
}