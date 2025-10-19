package com.learning.progress.service.impl;

import com.learning.progress.dto.chapter.ChapterDTO;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.excel.ImportChapterDTO;
import com.learning.progress.dto.chapter.SyncChapterRequest;
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

        // 5b. Validate duplicate chapter names (case-insensitive)
        Set<String> chapterNamesLower = new HashSet<>();
        for (SyncChapterRequest req : nonDeletedRequests) {
            String name = req.getChapterName().trim().toLowerCase();
            if (!chapterNamesLower.add(name)) {
                throw new ApiException(
                        String.format("Tên chapter bị trùng (không phân biệt hoa thường): %s", req.getChapterName()),
                        HttpStatus.BAD_REQUEST.value()
                );
            }
        }
        
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
        // Validate file
        validateExcelFile(file);

        List<ImportChapterDTO> importList = fileService.readExcelData(
                file, "Import Data", ImportChapterDTO.class
        );

        if (importList.isEmpty()) {
            throw new ApiException(
                    "File không có dữ liệu để import",
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        List<ChapterDTO> result = new ArrayList<>();

        // Nhóm theo syllabusCode
        Map<String, List<ImportChapterDTO>> chaptersBySyllabus = new LinkedHashMap<>();

        int rowIndex = 2; // Bắt đầu từ dòng 2 (sau header)
        for (ImportChapterDTO dto : importList) {
            String syllabusCode = dto.getSyllabusCode();

            // Validate syllabusCode không null
            if (syllabusCode == null || syllabusCode.trim().isEmpty()) {
                throw new ApiException(
                        String.format("Dòng %d, Cột 'Syllabus Code': Không được để trống", rowIndex),
                        HttpStatus.BAD_REQUEST.value()
                );
            }

            chaptersBySyllabus
                    .computeIfAbsent(syllabusCode.trim(), k -> new ArrayList<>())
                    .add(dto);

            rowIndex++;
        }

        // Xử lý từng syllabus
        int currentRow = 2;
        for (Map.Entry<String, List<ImportChapterDTO>> entry : chaptersBySyllabus.entrySet()) {
            String syllabusCode = entry.getKey();
            List<ImportChapterDTO> chapters = entry.getValue();

            // 1. Validate Syllabus tồn tại
            Syllabus syllabus = syllabusRepository.findBySyllabusCode(syllabusCode)
                    .filter(s -> s.getDeletedAt() == null)
                    .orElse(null);

            if (syllabus == null) {
                throw new ApiException(
                        String.format("Dòng %d, Cột 'Syllabus Code': Syllabus '%s' không tồn tại hoặc đã bị xóa",
                                currentRow, syllabusCode),
                        HttpStatus.BAD_REQUEST.value()
                );
            }

            // 2. Validate từng chapter
            Set<Integer> usedOrderNumbers = new HashSet<>();
            Set<String> usedChapterNames = new HashSet<>();

            for (int i = 0; i < chapters.size(); i++) {
                ImportChapterDTO dto = chapters.get(i);
                int rowNumber = currentRow + i;

                // Validate Chapter Name
                if (dto.getChapterName() == null || dto.getChapterName().trim().isEmpty()) {
                    throw new ApiException(
                            String.format("Dòng %d, Cột 'Chapter Name': Không được để trống", rowNumber),
                            HttpStatus.BAD_REQUEST.value()
                    );
                }

                String trimmedName = dto.getChapterName().trim();

                if (trimmedName.length() > 255) {
                    throw new ApiException(
                            String.format("Dòng %d, Cột 'Chapter Name': Vượt quá 255 ký tự (hiện tại: %d ký tự)",
                                    rowNumber, trimmedName.length()),
                            HttpStatus.BAD_REQUEST.value()
                    );
                }

                // Kiểm tra trùng tên trong cùng batch
                if (usedChapterNames.contains(trimmedName.toLowerCase())) {
                    throw new ApiException(
                            String.format("Dòng %d, Cột 'Chapter Name': Tên '%s' bị trùng lặp trong file",
                                    rowNumber, trimmedName),
                            HttpStatus.BAD_REQUEST.value()
                    );
                }
                usedChapterNames.add(trimmedName.toLowerCase());

                // Kiểm tra trùng tên với DB
                boolean exists = chapterRepository.existsBySyllabusAndChapterNameAndDeletedAtIsNull(
                        syllabus, trimmedName
                );
                if (exists) {
                    throw new ApiException(
                            String.format("Dòng %d, Cột 'Chapter Name': Tên '%s' đã tồn tại trong hệ thống cho syllabus '%s'",
                                    rowNumber, trimmedName, syllabusCode),
                            HttpStatus.BAD_REQUEST.value()
                    );
                }

                // Validate Order Number
                if (dto.getOrderNumber() == null) {
                    throw new ApiException(
                            String.format("Dòng %d, Cột 'Order Number': Không được để trống", rowNumber),
                            HttpStatus.BAD_REQUEST.value()
                    );
                }

                if (dto.getOrderNumber() < 1) {
                    throw new ApiException(
                            String.format("Dòng %d, Cột 'Order Number': Phải là số dương (>= 1), giá trị hiện tại: %d",
                                    rowNumber, dto.getOrderNumber()),
                            HttpStatus.BAD_REQUEST.value()
                    );
                }

                // Kiểm tra trùng lặp order number
                if (usedOrderNumbers.contains(dto.getOrderNumber())) {
                    throw new ApiException(
                            String.format("Dòng %d, Cột 'Order Number': Số thứ tự %d bị trùng lặp trong file",
                                    rowNumber, dto.getOrderNumber()),
                            HttpStatus.BAD_REQUEST.value()
                    );
                }
                usedOrderNumbers.add(dto.getOrderNumber());
            }

            // 3. Validate order numbers tuần tự không gap
            int maxOrder = Collections.max(usedOrderNumbers);
            for (int i = 1; i <= maxOrder; i++) {
                if (!usedOrderNumbers.contains(i)) {
                    throw new ApiException(
                            String.format("Syllabus '%s': Order Number phải tuần tự từ 1 đến %d không có gap. Thiếu số: %d",
                                    syllabusCode, maxOrder, i),
                            HttpStatus.BAD_REQUEST.value()
                    );
                }
            }

            // 4. Tạo mới chapters (nếu không có lỗi)
            for (ImportChapterDTO dto : chapters) {
                Chapter newChapter = new Chapter();
                newChapter.setSyllabus(syllabus);
                newChapter.setChapterName(dto.getChapterName().trim());
                newChapter.setOrderNumber(dto.getOrderNumber());

                Chapter saved = chapterRepository.saveAndFlush(newChapter);
                String chapterCode = DataUtil.generateChapterCode(saved.getId());
                saved.setChapterCode(chapterCode);
                saved = chapterRepository.save(saved);

                result.add(chapterMapper.toChapterDTO(saved));
            }

            currentRow += chapters.size();
        }

        return result;
    }

    // Validate Excel File
    private void validateExcelFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException("File không được để trống", HttpStatus.BAD_REQUEST.value());
        }

        String filename = file.getOriginalFilename();
        if (filename == null ||
                (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            throw new ApiException(
                    "File phải có định dạng Excel (.xlsx hoặc .xls)",
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // Validate file size (max 10MB)
        long maxSize = 10 * 1024 * 1024; // 10MB
        if (file.getSize() > maxSize) {
            throw new ApiException(
                    String.format("File vượt quá kích thước cho phép (Max: %dMB)", maxSize / (1024 * 1024)),
                    HttpStatus.BAD_REQUEST.value()
            );
        }
    }

}