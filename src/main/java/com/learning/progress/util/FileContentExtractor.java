package com.learning.progress.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Slf4j
public class FileContentExtractor {

    /**
     * Extract text content from uploaded file
     * Supports: TXT, DOCX, PDF
     */
    public static String extractContent(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("File must have a name");
        }

        String extension = getFileExtension(filename).toLowerCase();
        log.info("Extracting content from file: {} (type: {})", filename, extension);

        switch (extension) {
            case "txt":
                return extractFromTxt(file);
            case "docx":
                return extractFromDocx(file);
            case "pdf":
                return extractFromPdf(file);
            default:
                throw new IllegalArgumentException("Unsupported file type: " + extension +
                        ". Supported types: txt, docx, pdf");
        }
    }

    /**
     * Extract text from TXT file
     */
    private static String extractFromTxt(MultipartFile file) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }

            String result = content.toString().trim();
            log.info("Extracted {} characters from TXT file", result.length());
            return result;
        }
    }

    /**
     * Extract text from DOCX file
     */
    private static String extractFromDocx(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream();
             XWPFDocument document = new XWPFDocument(is);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {

            String content = extractor.getText().trim();
            log.info("Extracted {} characters from DOCX file", content.length());
            return content;
        }
    }

    /**
     * Extract text from PDF file
     */
    private static String extractFromPdf(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream();
             PDDocument document = PDDocument.load(is)) {

            PDFTextStripper stripper = new PDFTextStripper();
            String content = stripper.getText(document).trim();
            log.info("Extracted {} characters from PDF file ({} pages)",
                    content.length(), document.getNumberOfPages());
            return content;
        }
    }

    /**
     * Get file extension from filename
     */
    private static String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) {
            return "";
        }
        return filename.substring(lastDot + 1);
    }

    /**
     * Validate file size (max 10MB)
     */
    public static void validateFileSize(MultipartFile file) {
        long maxSize = 10 * 1024 * 1024; // 10MB
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException(
                    String.format("File size exceeds maximum allowed size of 10MB. File size: %.2f MB",
                            file.getSize() / (1024.0 * 1024.0)));
        }
    }

    /**
     * Validate file is not empty
     */
    public static void validateFileNotEmpty(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
    }
}
