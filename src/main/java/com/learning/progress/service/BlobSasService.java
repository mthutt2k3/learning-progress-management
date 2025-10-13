package com.learning.progress.service;

import org.springframework.web.multipart.MultipartFile;
import java.time.Duration;

public interface BlobSasService {
    String uploadFile(MultipartFile file);
    String generateSasUrl(String fileName, Duration expiry);
}
