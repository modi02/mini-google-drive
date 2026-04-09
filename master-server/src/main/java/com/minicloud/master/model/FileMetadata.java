package com.minicloud.master.model;

import java.time.Instant;
import java.util.List;

public class FileMetadata {

    private String filename;
    private long size;
    private List<String> replicaNodeIds;
    private Instant uploadedAt;

    public FileMetadata(String filename, long size, List<String> replicaNodeIds) {
        this.filename = filename;
        this.size = size;
        this.replicaNodeIds = replicaNodeIds;
        this.uploadedAt = Instant.now();
    }

    public String getFilename() { return filename; }
    public long getSize() { return size; }
    public List<String> getReplicaNodeIds() { return replicaNodeIds; }
    public Instant getUploadedAt() { return uploadedAt; }
}
