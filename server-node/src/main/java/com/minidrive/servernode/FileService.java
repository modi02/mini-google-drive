package com.minidrive.servernode;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileService {

    @Autowired
    private FileMetadataRepository repository;

    @Autowired
    private StorageNodeClient storageNodeClient;

    // UPLOAD: store file on 2 storage nodes, save metadata to MySQL
    public String uploadFile(MultipartFile file) {
        String fileName = file.getOriginalFilename();

        // Check if file already exists
        if (repository.existsByFileName(fileName)) {
            return "ERROR: File already exists: " + fileName;
        }

        List<String> allNodes = storageNodeClient.getAllStorageNodes();
        List<String> successfulNodes = new ArrayList<>();

        // Try uploading to each storage node (replication factor = 2)
        for (String nodeUrl : allNodes) {
            boolean success = storageNodeClient.uploadToNode(nodeUrl, fileName, file);
            if (success) {
                successfulNodes.add(nodeUrl);
                System.out.println("Uploaded to: " + nodeUrl);
            } else {
                System.out.println("Skipped (down): " + nodeUrl);
            }
        }

        // Need at least 1 node to have succeeded
        if (successfulNodes.isEmpty()) {
            return "ERROR: All storage nodes are down. Upload failed.";
        }

        // Save metadata to MySQL
        FileMetadata metadata = new FileMetadata();
        metadata.setFileName(fileName);
        metadata.setFileSize(file.getSize());
        metadata.setContentType(file.getContentType());
        metadata.setStorageNodes(String.join(",", successfulNodes));

        repository.save(metadata);

        return "SUCCESS: " + fileName + " stored on " +
               successfulNodes.size() + " node(s): " + successfulNodes;
    }

    // DOWNLOAD: look up which nodes have the file, try each until one works
    public byte[] downloadFile(String fileName) {
        // Look up metadata from MySQL
        FileMetadata metadata = repository.findByFileName(fileName)
                .orElse(null);

        if (metadata == null) {
            System.out.println("File not found in DB: " + fileName);
            return null;
        }

        // Get the list of nodes that have this file
        String[] nodes = metadata.getStorageNodes().split(",");

        // Try each node — if one is down, try the next (fault tolerance!)
        for (String nodeUrl : nodes) {
            byte[] fileBytes = storageNodeClient.downloadFromNode(nodeUrl.trim(), fileName);
            if (fileBytes != null) {
                System.out.println("Downloaded from: " + nodeUrl);
                return fileBytes;
            } else {
                System.out.println("Node unavailable, trying next: " + nodeUrl);
            }
        }

        System.out.println("All nodes failed for file: " + fileName);
        return null;
    }

    // LIST: return all active files from MySQL
    public List<FileMetadata> listFiles() {
        return repository.findByStatus("ACTIVE");
    }
}