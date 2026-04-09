package com.minicloud.storagenode.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FileStorageService {

    @Value("${storage.path:/app/storage-data}")
    private String storagePath;

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(Paths.get(storagePath));
    }

    public void save(String filename, MultipartFile file) throws IOException {
        Path target = Paths.get(storagePath, filename);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
    }

    public byte[] load(String filename) throws IOException {
        Path filePath = Paths.get(storagePath, filename);
        if (!Files.exists(filePath)) {
            throw new FileNotFoundException("File not found: " + filename);
        }
        return Files.readAllBytes(filePath);
    }

    public void delete(String filename) throws IOException {
        Path filePath = Paths.get(storagePath, filename);
        Files.deleteIfExists(filePath);
    }

    public List<String> listFiles() throws IOException {
        try (var stream = Files.list(Paths.get(storagePath))) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.toList());
        }
    }

    public int getFileCount() {
        try {
            return listFiles().size();
        } catch (IOException e) {
            return 0;
        }
    }
}
