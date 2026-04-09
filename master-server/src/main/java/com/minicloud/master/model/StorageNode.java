package com.minicloud.master.model;

public class StorageNode {

    private String nodeId;
    private String url;
    private volatile boolean alive;
    private volatile int fileCount;

    public StorageNode(String nodeId, String url) {
        this.nodeId = nodeId;
        this.url = url;
        this.alive = false;
        this.fileCount = 0;
    }

    public String getNodeId() { return nodeId; }
    public String getUrl() { return url; }
    public boolean isAlive() { return alive; }
    public int getFileCount() { return fileCount; }

    public void setAlive(boolean alive) { this.alive = alive; }
    public void setFileCount(int fileCount) { this.fileCount = fileCount; }

    @Override
    public String toString() {
        return "StorageNode{nodeId='" + nodeId + "', url='" + url +
               "', alive=" + alive + ", fileCount=" + fileCount + "}";
    }
}
