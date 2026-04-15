package com.minidrive.servernode;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {

    // Find a file by its name
    Optional<FileMetadata> findByFileName(String fileName);

    // Find all active files (status = 'ACTIVE')
    List<FileMetadata> findByStatus(String status);

    // Check if a file already exists
    boolean existsByFileName(String fileName);
}