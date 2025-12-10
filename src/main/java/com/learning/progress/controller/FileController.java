package com.learning.progress.controller;

import com.learning.progress.common.Const;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.service.BlobSasService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/file")
@Tag(name = "File", description = "File upload and access APIs for authenticated")
public class FileController {

    @Autowired
    private BlobSasService blobSasService;

    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Upload a file", description = "Upload a file to Blob storage and get its URL")
    public ResponseEntity<DataResponse<String>> uploadFile(@RequestParam("file") MultipartFile file) {
        String fileName = blobSasService.uploadFile(file);
        String fileUrl = blobSasService.generateSasUrl(fileName, Duration.ofDays(90));
        return new ResponseEntity<>(DataResponse.success(fileUrl, Const.RESULT_MESSAGE_CODE.CREATE_SUCCESSFUL), HttpStatus.CREATED);
    }

}
