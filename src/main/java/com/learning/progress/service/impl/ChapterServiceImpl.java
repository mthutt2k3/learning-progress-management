package com.learning.progress.service.impl;

import com.learning.progress.dto.ChapterDTO;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.dto.syllabus.ImportChapterDTO;
import com.learning.progress.dto.syllabus.SyncChapterRequest;
import com.learning.progress.entity.Chapter;
import com.learning.progress.entity.Syllabus;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.ChapterMapper;
import com.learning.progress.repository.ChapterRepository;
import com.learning.progress.repository.SyllabusRepository;
import com.learning.progress.service.BlobSasService;
import com.learning.progress.service.ChapterService;
import com.learning.progress.service.FileService;
import com.learning.progress.util.DataUtil;
import com.learning.progress.util.JwtUtil;
import jakarta.validation.ConstraintViolation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Validator;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Slf4j
public class ChapterServiceImpl implements ChapterService {

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private SyllabusRepository syllabusRepository;

    @Autowired
    private ChapterMapper chapterMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private Validator validator;

    @Autowired
    private FileService fileService;

    @Autowired
    private BlobSasService blobSasService;

    @Value("${azure.storage.chapter-template}")
    private String chapterTemplate;

    @Override
    public ChapterDTO getChapter(Long id) {
        Chapter chapter = chapterRepository.findById(id)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Chapter không tìm thấy hoặc đã bị xóa", HttpStatus.NOT_FOUND.value()));
        return chapterMapper.toChapterDTO(chapter);
    }

    @Override
    public DataResponse<List<ChapterDTO>> getChapterList(Long syllabusId, int page, int size, String searchText) {
        syllabusRepository.findById(syllabusId)
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Syllabus không tìm thấy hoặc đã bị xóa", HttpStatus.NOT_FOUND.value()));

        Page<Chapter> chapterPage = chapterRepository.findBySyllabusIdAndSearchText(syllabusId, searchText, PageRequest.of(page, size, Sort.by("orderNumber").ascending()));
        List<ChapterDTO> responses = chapterPage.getContent().stream()
                .map(chapterMapper::toChapterDTO)
                .collect(Collectors.toList());

        return DataResponse.<List<ChapterDTO>>builder()
                .traceId(org.slf4j.MDC.get("traceId"))
                .success(true)
                .message("Thành công")
                .data(responses)
                .timestamp(java.time.LocalDateTime.now())
                .page(page)
                .size(size)
                .totalElements(chapterPage.getTotalElements())
                .totalPages(chapterPage.getTotalPages())
                .build();
    }

    @Override
    @Transactional
    public List<ChapterDTO> syncChapters(Long syllabusId, List<SyncChapterRequest> request) {
        Syllabus syllabus = syllabusRepository.findById(syllabusId)
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Syllabus không tìm thấy", HttpStatus.NOT_FOUND.value()));

        // Lấy tất cả active chapters hiện tại
        List<Chapter> existingActiveChapters = chapterRepository
                .findBySyllabusIdAndDeletedAtIsNullOrderByOrderNumberAsc(syllabusId);

        Set<Long> existingActiveIds = existingActiveChapters.stream()
                .map(Chapter::getId)
                .collect(Collectors.toSet());

        // 1. Separate requests: deleted vs non-deleted
        List<SyncChapterRequest> deleteRequests = request.stream()
                .filter(SyncChapterRequest::isToBeDeleted)
                .collect(Collectors.toList());

        List<SyncChapterRequest> nonDeletedRequests = request.stream()
                .filter(req -> !req.isToBeDeleted())
                .collect(Collectors.toList());

        // 2. Validate DELETE requests (chỉ cần ID)
        for (SyncChapterRequest deleteReq : deleteRequests) {
            // Validate ID required cho delete
            Set<ConstraintViolation<SyncChapterRequest>> violations = validator.validate(deleteReq, SyncChapterRequest.Deleted.class);
            if (!violations.isEmpty()) {
                String errorMsg = violations.stream()
                        .map(ConstraintViolation::getMessage)
                        .collect(Collectors.joining(", "));
                throw new ApiException(errorMsg, HttpStatus.BAD_REQUEST.value());
            }

            Long deleteId = deleteReq.getId();
            if (deleteId == null || !existingActiveIds.contains(deleteId)) {
                throw new ApiException(
                        "Chapter ID để xóa không tồn tại hoặc đã bị xóa: " + deleteId,
                        HttpStatus.BAD_REQUEST.value()
                );
            }
        }

        // 3. Validate EXISTING IDs trong non-deleted requests
        Set<Long> existingUpdateIds = nonDeletedRequests.stream()
                .filter(req -> req.getId() != null) // Existing chapters
                .map(SyncChapterRequest::getId)
                .collect(Collectors.toSet());

        Set<Long> invalidExistingIds = existingUpdateIds.stream()
                .filter(id -> !existingActiveIds.contains(id))
                .collect(Collectors.toSet());

        if (!invalidExistingIds.isEmpty()) {
            throw new ApiException(
                    "Các existing chapter ID không tồn tại: " + invalidExistingIds,
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // 4. Validate non-deleted requests với full validation
        for (SyncChapterRequest req : nonDeletedRequests) {
            Set<ConstraintViolation<SyncChapterRequest>> violations = validator.validate(req, SyncChapterRequest.NotDeleted.class);
            if (!violations.isEmpty()) {
                String errorMsg = violations.stream()
                        .map(ConstraintViolation::getMessage)
                        .collect(Collectors.joining(", "));
                throw new ApiException(errorMsg, HttpStatus.BAD_REQUEST.value());
            }
        }

        // 5. Validate EXISTING IDs - Strict Matching
        Set<Long> requestExistingIds = nonDeletedRequests.stream()
                .filter(req -> req.getId() != null) // Existing chapters cần update
                .map(SyncChapterRequest::getId)
                .collect(Collectors.toSet());

        Set<Long> requestDeleteIds = deleteRequests.stream()
                .map(SyncChapterRequest::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Check 1: Tất cả request IDs phải tồn tại trong DB active chapters
        Set<Long> invalidRequestIds = new HashSet<>();
        invalidRequestIds.addAll(requestExistingIds.stream()
                .filter(id -> !existingActiveIds.contains(id))
                .collect(Collectors.toSet()));
        invalidRequestIds.addAll(requestDeleteIds.stream()
                .filter(id -> !existingActiveIds.contains(id))
                .collect(Collectors.toSet()));

        if (!invalidRequestIds.isEmpty()) {
            throw new ApiException(
                    "Các chapter ID không tồn tại hoặc đã bị xóa: " + invalidRequestIds,
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // Check 2: Tất cả DB active chapters phải được handle (update HOẶC delete)
        Set<Long> handledIds = new HashSet<>();
        handledIds.addAll(requestExistingIds);
        handledIds.addAll(requestDeleteIds);

        Set<Long> unhandledDbIds = existingActiveIds.stream()
                .filter(id -> !handledIds.contains(id))
                .collect(Collectors.toSet());

        if (!unhandledDbIds.isEmpty()) {
            throw new ApiException(
                    String.format("Các chapter sau không được handle trong sync request: %s. FE phải bao gồm tất cả active chapters!", unhandledDbIds),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // Check 3: Verify exact matching logic
        int expectedNonDeletedCount = existingActiveChapters.size() - requestDeleteIds.size();
        int actualNonDeletedCount = requestExistingIds.size();

        if (actualNonDeletedCount != expectedNonDeletedCount) {
            throw new ApiException(
                    String.format("Số lượng non-deleted chapters không khớp! Expected: %d, Actual: %d",
                            expectedNonDeletedCount, actualNonDeletedCount),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        log.info("Strict ID validation passed: DB active={}, handled={}, existing={}, delete={}",
                existingActiveIds.size(), handledIds.size(), requestExistingIds.size(), requestDeleteIds.size());


        Set<Integer> orderNumbers = nonDeletedRequests.stream()
                .map(SyncChapterRequest::getOrderNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        int nonDeletedSize = nonDeletedRequests.size();
        Set<Integer> expectedOrders = IntStream.rangeClosed(1, nonDeletedSize).boxed().collect(Collectors.toSet());

        if (orderNumbers.size() != nonDeletedSize || !orderNumbers.equals(expectedOrders)) {
            throw new ApiException(
                    String.format("Order numbers phải tuần tự từ 1 đến %d không trùng lặp và không có gap. Current: %s",
                            nonDeletedSize, orderNumbers),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        log.info("Order number validation passed: nonDeletedSize={}, orders={}", nonDeletedSize, orderNumbers);
        // 6. Initial process
        String currentUser = jwtUtil.extractUsernameFromCurrentRequest();
        OffsetDateTime now = OffsetDateTime.now();
        List<ChapterDTO> result = new ArrayList<>();

        // 7. Process DELETE (chỉ cần ID)
        for (SyncChapterRequest deleteReq : deleteRequests) {
            Chapter chapter = chapterRepository.findById(deleteReq.getId())
                    .filter(c -> c.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException("Chapter không tìm thấy để xóa", HttpStatus.NOT_FOUND.value()));
            chapter.setDeletedBy(currentUser);
            chapter.setDeletedAt(now);
            chapterRepository.save(chapter);
        }

        // 8. Process UPDATE existing
        List<SyncChapterRequest> updateRequests = nonDeletedRequests.stream()
                .filter(req -> req.getId() != null)
                .collect(Collectors.toList());

        for (SyncChapterRequest req : updateRequests) {
            Chapter chapter = chapterRepository.findById(req.getId())
                    .filter(c -> c.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException("Chapter không tìm thấy: " + req.getId(), HttpStatus.NOT_FOUND.value()));

            chapter.setChapterName(req.getChapterName());
            chapter.setOrderNumber(req.getOrderNumber());
            chapter.setUpdatedBy(currentUser);
            chapter.setUpdatedAt(now);
            Chapter saved = chapterRepository.save(chapter);
            result.add(chapterMapper.toChapterDTO(saved));
        }

        // 9. Process CREATE new (id = null)
        List<SyncChapterRequest> newRequests = nonDeletedRequests.stream()
                .filter(req -> req.getId() == null)
                .collect(Collectors.toList());

        for (SyncChapterRequest req : newRequests) {
            Chapter newChapter = new Chapter();
            newChapter.setSyllabus(syllabus);
            newChapter.setChapterName(req.getChapterName());
            newChapter.setOrderNumber(req.getOrderNumber());
            newChapter.setCreatedBy(currentUser);
            newChapter.setUpdatedBy(currentUser);
            newChapter.setUpdatedAt(now);

            Chapter saved = chapterRepository.saveAndFlush(newChapter);
            String chapterCode = DataUtil.generateChapterCode(saved.getId());
            saved.setChapterCode(chapterCode);

            saved = chapterRepository.save(saved);
            result.add(chapterMapper.toChapterDTO(saved));
        }

        return result;
    }

    @Override
    public byte[] generateChapterImportTemplate() {
        return fileService.generateChapterImportTemplate();
    }

    @Override
    public String getChapterTemplateSasUrl() {
        return blobSasService.generateSasUrl(chapterTemplate, Duration.ofMinutes(30));
    }

    @Override
    @Transactional
    public List<ChapterDTO> importChaptersFromExcel(MultipartFile file) {
        List<ImportChapterDTO> importList = fileService.readExcelData(file, "Import Data", ImportChapterDTO.class);
        List<ChapterDTO> result = new ArrayList<>();
        String currentUser = jwtUtil.extractUsernameFromCurrentRequest();
        OffsetDateTime now = OffsetDateTime.now();

        // Nhóm theo syllabusCode
        Map<String, List<ImportChapterDTO>> chaptersBySyllabus = importList.stream()
                .collect(Collectors.groupingBy(ImportChapterDTO::getSyllabusCode));

        for (Map.Entry<String, List<ImportChapterDTO>> entry : chaptersBySyllabus.entrySet()) {
            String syllabusCode = entry.getKey();
            List<ImportChapterDTO> chapters = entry.getValue();

            // 1. Kiểm tra syllabusCode
            Syllabus syllabus = syllabusRepository.findBySyllabusCode(syllabusCode)
                    .filter(s -> s.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException("Syllabus không tìm thấy hoặc đã bị xóa với mã: " + syllabusCode, HttpStatus.NOT_FOUND.value()));

            // 2. Validate chapters
            for (ImportChapterDTO req : chapters) {
                if (req.getChapterName() == null || req.getChapterName().trim().isEmpty()) {
                    throw new ApiException("Chapter name là bắt buộc: " + req.getChapterName(), HttpStatus.BAD_REQUEST.value());
                }
                if (req.getChapterName().length() > 255) {
                    throw new ApiException("Chapter name vượt quá 255 ký tự: " + req.getChapterName(), HttpStatus.BAD_REQUEST.value());
                }
                if (req.getOrderNumber() == null || req.getOrderNumber() < 1) {
                    throw new ApiException("Order number phải là số dương: " + req.getOrderNumber(), HttpStatus.BAD_REQUEST.value());
                }
            }

            // 3. Validate order numbers
            Set<Integer> orderNumbers = chapters.stream()
                    .map(ImportChapterDTO::getOrderNumber)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            int chapterSize = chapters.size();
            Set<Integer> expectedOrders = IntStream.rangeClosed(1, chapterSize).boxed().collect(Collectors.toSet());

            if (orderNumbers.size() != chapterSize || !orderNumbers.equals(expectedOrders)) {
                throw new ApiException(
                        String.format("Order numbers phải tuần tự từ 1 đến %d, không trùng lặp và không có gap. Current: %s",
                                chapterSize, orderNumbers),
                        HttpStatus.BAD_REQUEST.value()
                );
            }

            log.info("Validation passed for syllabusCode={}: total chapters={}", syllabusCode, chapterSize);

            // 4. Tạo mới chapters
            for (ImportChapterDTO req : chapters) {
                Chapter newChapter = new Chapter();
                newChapter.setSyllabus(syllabus);
                newChapter.setChapterName(req.getChapterName());
                newChapter.setOrderNumber(req.getOrderNumber());
                newChapter.setCreatedBy(currentUser);
                newChapter.setUpdatedBy(currentUser);
                newChapter.setUpdatedAt(now);
                Chapter saved = chapterRepository.saveAndFlush(newChapter);
                String chapterCode = DataUtil.generateChapterCode(saved.getId());
                saved.setChapterCode(chapterCode);

                saved = chapterRepository.save(saved);
                result.add(chapterMapper.toChapterDTO(saved));
            }
        }

        return result;
    }

}