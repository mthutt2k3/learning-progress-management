package com.learning.progress.service.impl;

import com.learning.progress.common.*;
import com.learning.progress.dto.clazz.chapter.ClassChapterDTO;
import com.learning.progress.dto.clazz.chapter.SyncClassChapterRequest;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.excel.ImportChapterInClassDTO;
import com.learning.progress.entity.ClassChapter;
import com.learning.progress.entity.Clazz;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.ClassChapterMapper;
import com.learning.progress.repository.ClassChapterRepository;
import com.learning.progress.repository.ClassRepository;
import com.learning.progress.repository.ClassTeacherRepository;
import com.learning.progress.service.BlobSasService;
import com.learning.progress.service.ClassChapterService;
import com.learning.progress.service.ClassHistoryService;
import com.learning.progress.service.FileService;
import com.learning.progress.util.DataUtil;
import com.learning.progress.util.JwtUtil;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Slf4j
public class ClassChapterServiceImpl implements ClassChapterService {

    @Autowired
    private ClassChapterRepository classChapterRepository;
    @Autowired
    private ClassRepository classRepository;
    @Autowired
    private ClassTeacherRepository classTeacherRepository;
    @Autowired
    private ClassHistoryService classHistoryService;
    @Autowired
    private ClassChapterMapper classChapterMapper;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private Validator validator;
    @Autowired
    private FileService fileService;

    @Autowired
    private BlobSasService blobSasService;

    @Value("${azure.storage.chapter-in-class-template}")
    private String chapterInClassTemplate;

    @Transactional
    @Override
    public List<ClassChapterDTO> syncClassChapters(Long classId, List<SyncClassChapterRequest> request) {
        // Validate class
        Clazz classEntity = classRepository.findById(classId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        if(classEntity.getStatus() == ClassStatus.FINISHED){
            throw new ApiException(Const.CLASS.FINISHED_CLASS, HttpStatus.BAD_REQUEST.value());
        }
        // Load existing active class chapters
        List<ClassChapter> existingActiveChapters = classChapterRepository
                .findByClassIdAndDeletedAtIsNullOrderByOrderNumberAsc(classId);
        Set<Long> existingActiveIds = existingActiveChapters.stream()
                .map(ClassChapter::getId)
                .collect(Collectors.toSet());

        // Phân loại request
        List<SyncClassChapterRequest> deleteRequests = request.stream()
                .filter(SyncClassChapterRequest::isToBeDeleted)
                .collect(Collectors.toList());
        List<SyncClassChapterRequest> nonDeletedRequests = request.stream()
                .filter(req -> !req.isToBeDeleted())
                .collect(Collectors.toList());

        // Validate DELETE
        for (SyncClassChapterRequest deleteReq : deleteRequests) {
            Set<ConstraintViolation<SyncClassChapterRequest>> violations = validator.validate(deleteReq, SyncClassChapterRequest.Deleted.class);
            if (!violations.isEmpty()) {
                throw new ApiException(violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(", ")), HttpStatus.BAD_REQUEST.value());
            }
            Long deleteId = deleteReq.getId();
            if (deleteId == null || !existingActiveIds.contains(deleteId)) {
                throw new ApiException("Clazz chapter ID không tồn tại: " + deleteId, HttpStatus.BAD_REQUEST.value());
            }
        }
        // 3. Validate EXISTING IDs trong non-deleted requests
        Set<Long> existingUpdateIds = nonDeletedRequests.stream()
                .filter(req -> req.getId() != null) // Existing chapters
                .map(SyncClassChapterRequest::getId)
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
        // Validate non-deleted
        for (SyncClassChapterRequest req : nonDeletedRequests) {
            Set<ConstraintViolation<SyncClassChapterRequest>> violations = validator.validate(req, SyncClassChapterRequest.NotDeleted.class);
            if (!violations.isEmpty()) {
                throw new ApiException(violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(", ")), HttpStatus.BAD_REQUEST.value());
            }
        }

        // 5. Validate EXISTING IDs - Strict Matching
        Set<Long> requestExistingIds = nonDeletedRequests.stream()
                .filter(req -> req.getId() != null) // Existing chapters cần update
                .map(SyncClassChapterRequest::getId)
                .collect(Collectors.toSet());

        Set<Long> requestDeleteIds = deleteRequests.stream()
                .map(SyncClassChapterRequest::getId)
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


        // Validate order numbers
        Set<Integer> orderNumbers = nonDeletedRequests.stream()
                .map(SyncClassChapterRequest::getOrderNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        int nonDeletedSize = nonDeletedRequests.size();
        Set<Integer> expectedOrders = IntStream.rangeClosed(1, nonDeletedSize).boxed().collect(Collectors.toSet());
        if (orderNumbers.size() != nonDeletedSize || !orderNumbers.equals(expectedOrders)) {
            throw new ApiException("Order numbers phải tuần tự từ 1 đến " + nonDeletedSize, HttpStatus.BAD_REQUEST.value());
        }

        OffsetDateTime now = OffsetDateTime.now();
        Long actionByUserId = jwtUtil.extractUserIdFromCurrentRequest();
        List<ClassChapterDTO> result = new ArrayList<>();
        String visibleToRoles = String.format("%s,%s,%s", RoleName.MANAGER.name(), RoleName.TEACHER.name(), RoleName.TEACHING_ASSISTANT.name());

        // Process DELETE
        for (SyncClassChapterRequest deleteReq : deleteRequests) {
            ClassChapter classChapter = classChapterRepository.findById(deleteReq.getId())
                    .filter(c -> c.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException(Const.CLASS_CHAPTER.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
            classChapter.setDeletedBy(jwtUtil.extractEmailPrefixFromCurrentRequest());
            classChapter.setDeletedAt(now);
            classChapterRepository.save(classChapter);

            // Ghi lịch sử
            String actionDetails = String.format(
                    Const.CLASS_CHAPTER.ACTION_DELETE,
                    classChapter.getClassChapterName(),
                    classEntity.getClassName()
            );
            classHistoryService.saveClassHistory(
                    classId,
                    actionDetails,
                    actionByUserId,
                    ActionType.DELETE_CHAPTER.name(),
                    visibleToRoles
            );

        }

        // Process UPDATE
        List<SyncClassChapterRequest> updateRequests = nonDeletedRequests.stream()
                .filter(req -> req.getId() != null)
                .collect(Collectors.toList());
        for (SyncClassChapterRequest req : updateRequests) {
            ClassChapter classChapter = classChapterRepository.findById(req.getId())
                    .filter(c -> c.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException(Const.CLASS_CHAPTER.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
            classChapter.setClassChapterName(req.getClassChapterName());
            classChapter.setOrderNumber(req.getOrderNumber());
            result.add(classChapterMapper.toClassChapterDTO(classChapterRepository.save(classChapter)));

            // Ghi lịch sử
            String actionDetails = String.format(
                    Const.CLASS_CHAPTER.ACTION_UPDATE,
                    req.getClassChapterName(),
                    classEntity.getClassName(),
                    req.getOrderNumber()
            );
            classHistoryService.saveClassHistory(
                    classId,
                    actionDetails,
                    actionByUserId,
                    ActionType.UPDATE_CHAPTER.name(),
                    visibleToRoles
            );
        }

        // Process CREATE
        List<SyncClassChapterRequest> newRequests = nonDeletedRequests.stream()
                .filter(req -> req.getId() == null)
                .collect(Collectors.toList());
        for (SyncClassChapterRequest req : newRequests) {
            ClassChapter newChapter = new ClassChapter();
            newChapter.setClazz(classEntity);
            newChapter.setClassChapterName(req.getClassChapterName());
            newChapter.setOrderNumber(req.getOrderNumber());
            result.add(classChapterMapper.toClassChapterDTO(classChapterRepository.save(newChapter)));

            ClassChapter saved = classChapterRepository.saveAndFlush(newChapter);
            String chapterCode = DataUtil.generateClassChapterCode(saved.getId(), saved.getClazz().getId());
            saved.setClassChapterCode(chapterCode);

            result.add(classChapterMapper.toClassChapterDTO(classChapterRepository.save(saved)));

            // Ghi lịch sử
            String actionDetails = String.format(
                    Const.CLASS_CHAPTER.ACTION_CREATE,
                    req.getClassChapterName(),
                    classEntity.getClassName(),
                    req.getOrderNumber()
            );
            classHistoryService.saveClassHistory(
                    classId,
                    actionDetails,
                    actionByUserId,
                    ActionType.CREATE_CHAPTER.name(),
                    visibleToRoles
            );
        }

        return result;
    }

    public ClassChapterDTO getClassChapter(Long id) {
        ClassChapter classChapter = classChapterRepository.findById(id)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(Const.CLASS_CHAPTER.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
        return classChapterMapper.toClassChapterDTO(classChapter);
    }

    public DataResponse<List<ClassChapterDTO>> getClassChapterList(Long classId, int page, int size, String searchText) {
        classRepository.findById(classId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        Page<ClassChapter> chapterPage = classChapterRepository.findByClassIdAndSearchText(classId, searchText, PageRequest.of(page, size, Sort.by("orderNumber").ascending()));
        List<ClassChapterDTO> responses = chapterPage.getContent().stream()
                .map(classChapterMapper::toClassChapterDTO)
                .collect(Collectors.toList());

        return DataResponse.<List<ClassChapterDTO>>builder()
                .success(true)
                .message(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .data(responses)
                .page(page)
                .size(size)
                .totalElements(chapterPage.getTotalElements())
                .totalPages(chapterPage.getTotalPages())
                .build();
    }

    @Override
    public byte[] generateClassChaptersImportTemplate() {
        return fileService.generateChapterInClassImportTemplate();
    }

    @Override
    public String getClassChaptersTemplateSasUrl() {
        return blobSasService.generateSasUrl(chapterInClassTemplate, Duration.ofMinutes(30));
    }

    @Override
    @Transactional
    public List<ClassChapterDTO> importClassChaptersFromExcel(Long classId, MultipartFile file) {
        // Validate class
        Clazz classEntity = classRepository.findById(classId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(Const.CLASS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // Validate teacher assignment
        Long currentUserId = jwtUtil.extractUserIdFromCurrentRequest();
        if ( !classTeacherRepository.existsByClazz_IdAndUser_IdAndStatus(classId, currentUserId, ClassTeacherStatus.ACTIVE)) {
            throw new ApiException(Const.CLASS.TEACHER_NOT_ASSIGNED, HttpStatus.FORBIDDEN.value());
        }

        // Read Excel data
        List<ImportChapterInClassDTO> importList = fileService.readExcelData(
                file, "Import Data", ImportChapterInClassDTO.class);

        List<ClassChapterDTO> result = new ArrayList<>();
        String currentUser = jwtUtil.extractUsernameFromCurrentRequest();
        OffsetDateTime now = OffsetDateTime.now();
        Long actionByUserId = jwtUtil.extractUserIdFromCurrentRequest();
        String visibleToRoles = String.format("%s,%s,%s",
                RoleName.MANAGER.name(),
                RoleName.TEACHER.name(),
                RoleName.TEACHING_ASSISTANT.name());

        // Group by classCode
        Map<String, List<ImportChapterInClassDTO>> chaptersByClass = importList.stream()
                .collect(Collectors.groupingBy(ImportChapterInClassDTO::getClassCode));

        for (Map.Entry<String, List<ImportChapterInClassDTO>> entry : chaptersByClass.entrySet()) {
            String classCode = entry.getKey();
            List<ImportChapterInClassDTO> chapters = entry.getValue();

            // Validate classCode
            if (!classEntity.getClassCode().equals(classCode)) {
                throw new ApiException("Class code không khớp: " + classCode, HttpStatus.BAD_REQUEST.value());
            }

            // Validate chapters
            for (ImportChapterInClassDTO req : chapters) {
                if (req.getChapterName() == null || req.getChapterName().trim().isEmpty()) {
                    throw new ApiException("Chapter name là bắt buộc: " + req.getChapterName(),
                            HttpStatus.BAD_REQUEST.value());
                }
                if (req.getChapterName().length() > 255) {
                    throw new ApiException("Chapter name vượt quá 255 ký tự: " + req.getChapterName(),
                            HttpStatus.BAD_REQUEST.value());
                }
                if (req.getOrderNumber() == null || req.getOrderNumber() < 1) {
                    throw new ApiException("Order number phải là số dương: " + req.getOrderNumber(),
                            HttpStatus.BAD_REQUEST.value());
                }
            }

            // Validate order numbers
            Set<Integer> orderNumbers = chapters.stream()
                    .map(ImportChapterInClassDTO::getOrderNumber)
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

            log.info("Validation passed for classCode={}: total chapters={}", classCode, chapterSize);

            // Create new class chapters
            for (ImportChapterInClassDTO req : chapters) {
                ClassChapter newChapter = new ClassChapter();
                newChapter.setClazz(classEntity);
                newChapter.setClassChapterName(req.getChapterName());
                newChapter.setOrderNumber(req.getOrderNumber());
                newChapter.setCreatedBy(currentUser);
                newChapter.setUpdatedBy(currentUser);
                newChapter.setCreatedAt(now);
                newChapter.setUpdatedAt(now);

                ClassChapter saved = classChapterRepository.saveAndFlush(newChapter);
                String chapterCode = DataUtil.generateClassChapterCode(saved.getId(), saved.getClazz().getId());
                saved.setClassChapterCode(chapterCode);
                saved = classChapterRepository.save(saved);
                result.add(classChapterMapper.toClassChapterDTO(saved));

                // Record history
                String actionDetails = String.format(
                        Const.CLASS_CHAPTER.ACTION_CREATE,
                        req.getChapterName(),
                        classEntity.getClassName(),
                        req.getOrderNumber()
                );
                classHistoryService.saveClassHistory(
                        classId,
                        actionDetails,
                        actionByUserId,
                        ActionType.CREATE_CHAPTER.name(),
                        visibleToRoles
                );
            }
        }

        return result;
    }
}