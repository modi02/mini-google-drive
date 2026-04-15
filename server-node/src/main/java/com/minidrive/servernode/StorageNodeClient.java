package com.minidrive.servernode;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Component
public class StorageNodeClient {

    private final RestTemplate restTemplate = new RestTemplate();

    // Reads the comma-separated list from application.properties
    @Value("${storage.nodes}")
    private String storageNodesConfig;

    // Returns list of all storage node URLs
    public List<String> getAllStorageNodes() {
        return Arrays.asList(storageNodesConfig.split(","));
    }

    // Upload a file to a specific storage node
    // Returns true if successful, false if that node is down
    public boolean uploadToNode(String nodeUrl, String fileName, MultipartFile file) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            // Wrap the file bytes into a resource Spring can send over HTTP
            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", fileResource);

            HttpEntity<MultiValueMap<String, Object>> requestEntity =
                    new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    nodeUrl + "/files/upload",
                    requestEntity,
                    String.class
            );

            return response.getStatusCode() == HttpStatus.OK;

        } catch (Exception e) {
            System.out.println("Failed to upload to node: " + nodeUrl + " — " + e.getMessage());
            return false;
        }
    }

    // Download a file from a specific storage node
    // Returns the file bytes, or null if that node is down
    public byte[] downloadFromNode(String nodeUrl, String fileName) {
        try {
            ResponseEntity<byte[]> response = restTemplate.getForEntity(
                    nodeUrl + "/files/download/" + fileName,
                    byte[].class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            }
            return null;

        } catch (Exception e) {
            System.out.println("Failed to download from node: " + nodeUrl + " — " + e.getMessage());
            return null;
        }
    }
}