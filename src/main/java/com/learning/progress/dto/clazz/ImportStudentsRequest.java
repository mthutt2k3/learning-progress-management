package com.learning.progress.dto.clazz;

import org.springframework.web.multipart.MultipartFile;

public class ImportStudentsRequest {
    private MultipartFile file;

    public ImportStudentsRequest(MultipartFile file) {
        this.file = file;
    }

    public MultipartFile getFile() {
        return file;
    }
}
