package com.learning.progress.service.impl;

import com.learning.progress.common.ClassStatus;
import com.learning.progress.common.Const;
import com.learning.progress.dto.excel.ValidationResult;
import com.learning.progress.dto.syllabus.SyllabusDTO;
import com.learning.progress.dto.syllabus.SyllabusDetailDTO;
import com.learning.progress.dto.excel.ExportSyllabusDTO;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.dto.syllabus.CreateSyllabusRequest;
import com.learning.progress.dto.excel.ImportSyllabusDTO;
import com.learning.progress.dto.syllabus.UpdateSyllabusRequest;
import com.learning.progress.entity.Chapter;
import com.learning.progress.entity.Lesson;
import com.learning.progress.entity.Level;
import com.learning.progress.entity.Syllabus;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.SyllabusMapper;
import com.learning.progress.repository.ClassRepository;
import com.learning.progress.repository.LevelRepository;
import com.learning.progress.repository.SyllabusRepository;
import com.learning.progress.service.BlobSasService;
import com.learning.progress.service.FileService;
import com.learning.progress.service.SyllabusService;
import com.learning.progress.util.DataUtil;
import com.learning.progress.util.JwtUtil;
import com.learning.progress.util.TraceUtil;
import com.vladmihalcea.hibernate.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SyllabusServiceImpl implements SyllabusService {

    @Autowired
    private SyllabusRepository syllabusRepository;

    @Autowired
    private LevelRepository levelRepository;

    @Autowired
    private SyllabusMapper syllabusMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private FileService fileService;

    @Autowired
    private BlobSasService blobSasService;

    // NEW: notification service
    @Autowired
    private com.learning.progress.service.NotificationService notificationService;

    @Value("${azure.storage.syllabus-template}")
    private String syllabusTemplate;
    @Autowired
    private ClassRepository classRepository;

    @Override
    @Transactional
    public SyllabusDTO createSyllabus(CreateSyllabusRequest request) {
        String syllabusNameNormalized = DataUtil.normalize(request.getSyllabusName());
        if (syllabusRepository.existsBySyllabusNameIgnoreCase(syllabusNameNormalized)) {
            throw new ApiException(Const.SYLLABUS.EXIST_NAME, HttpStatus.BAD_REQUEST.value());
        }

        Level level = levelRepository.findByIdAndDeletedAtIsNull(request.getLevelId())
                .orElseThrow(() -> new ApiException(Const.SYLLABUS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        Syllabus syllabus = syllabusMapper.toSyllabus(request);
        syllabus.setSyllabusName(syllabusNameNormalized);
        syllabus.setLevel(level);
        syllabusRepository.save(syllabus);

        String syllabusCode = DataUtil.generateSyllabusCode(syllabus.getId());
        syllabus.setSyllabusCode(syllabusCode);
        syllabusRepository.saveAndFlush(syllabus);

        // notify actor
        try {
            Long actor = jwtUtil.extractUserIdFromCurrentRequest();
            String title = "Tạo syllabus thành công";
            String message = "Bạn đã tạo syllabus \"" + syllabus.getSyllabusName() + "\".";
            notificationService.createNotification(actor, null, title, message, null, null);
        } catch (Exception ex) {
            log.debug("Failed to send createSyllabus notification: {}", ex.getMessage());
        }

        return syllabusMapper.toSyllabusDTO(syllabus);
    }

    @Override
    @Transactional
    public SyllabusDTO updateSyllabus(Long id, UpdateSyllabusRequest request) {
        String syllabusNameNormalized = DataUtil.normalize(request.getSyllabusName());
        if (syllabusRepository.existsBySyllabusNameIgnoreCaseAndIdNot(syllabusNameNormalized)) {
            throw new ApiException(Const.SYLLABUS.EXIST_NAME, HttpStatus.BAD_REQUEST.value());
        }

        Syllabus syllabus = syllabusRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(Const.SYLLABUS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        Level level = levelRepository.findByIdAndDeletedAtIsNull(request.getLevelId())
                .orElseThrow(() -> new ApiException(Const.LEVEL.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        Syllabus updatedSyllabus = syllabusMapper.toSyllabus(request);
        syllabus.setSyllabusName(syllabusNameNormalized);
        syllabus.setLevel(level);
        syllabus.setDescription(updatedSyllabus.getDescription());
        syllabusRepository.save(syllabus);

        // notify actor
        try {
            Long actor = jwtUtil.extractUserIdFromCurrentRequest();
            String title = "Cập nhật syllabus thành công";
            String message = "Bạn đã cập nhật syllabus \"" + syllabus.getSyllabusName() + "\".";
            notificationService.createNotification(actor, null, title, message, null, null);
        } catch (Exception ex) {
            log.debug("Failed to send updateSyllabus notification: {}", ex.getMessage());
        }

        return syllabusMapper.toSyllabusDTO(syllabus);
    }

    @Override
    @Transactional
    public void deleteSyllabus(Long id) {
        Syllabus syllabus = syllabusRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(Const.SYLLABUS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        boolean hasActiveClasses = classRepository.existsBySyllabusIdAndStatusNotAndDeletedAtIsNull(
                id, ClassStatus.FINISHED);

        if (hasActiveClasses) {
            throw new ApiException(Const.SYLLABUS.IN_USE_BY_ACTIVE_CLASS, HttpStatus.BAD_REQUEST.value());
        }

        syllabus.setDeletedBy(jwtUtil.extractUsernameFromCurrentRequest());
        syllabus.setDeletedAt(OffsetDateTime.now());
        syllabusRepository.save(syllabus);

        // notify actor
        try {
            Long actor = jwtUtil.extractUserIdFromCurrentRequest();
            String title = "Xóa syllabus";
            String message = "Bạn đã xóa syllabus \"" + syllabus.getSyllabusName() + "\".";
            notificationService.createNotification(actor, null, title, message, null, null);
        } catch (Exception ex) {
            log.debug("Failed to send deleteSyllabus notification: {}", ex.getMessage());
        }
    }

    @Override
    public SyllabusDetailDTO getSyllabusDetail(Long id, String include) {
        Syllabus syllabus = syllabusRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(Const.SYLLABUS.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        SyllabusDetailDTO syllabusDetailDTO = syllabusMapper.toSyllabusDetailDTO(syllabus);

        switch (include.toUpperCase()) {
            case "CHAPTERS":
                syllabusDetailDTO.setChapterListInSyllabus(
                        syllabusRepository.findChaptersBySyllabusId(id)
                                .stream()
                                .map(chapter -> SyllabusDetailDTO.ChapterInSyllabus.builder()
                                        .chapterId(chapter.getId())
                                        .chapterName(chapter.getChapterName())
                                        .orderNumber(chapter.getOrderNumber())
                                        .build())
                                .collect(Collectors.toList()));
                syllabusDetailDTO.setLessonListInSyllabus(null);
                break;
            case "LESSONS":
                syllabusDetailDTO.setChapterListInSyllabus(null);
                syllabusDetailDTO.setLessonListInSyllabus(
                        syllabusRepository.findLessonsBySyllabusId(id)
                                .stream()
                                .map(lesson -> SyllabusDetailDTO.LessonInSyllabus.builder()
                                        .id(lesson.getId())
                                        .lessonName(lesson.getLessonName())
                                        .content(lesson.getContent())
                                        .orderNumber(lesson.getOrderNumber())
                                        .chapter(SyllabusDetailDTO.ChapterInSyllabus.builder()
                                                .chapterId(lesson.getChapter().getId())
                                                .chapterName(lesson.getChapter().getChapterName())
                                                .orderNumber(lesson.getChapter().getOrderNumber())
                                                .build())
                                        .build())
                                .collect(Collectors.toList()));
                break;
            case "ALL":
                syllabusDetailDTO.setChapterListInSyllabus(
                        syllabusRepository.findChaptersBySyllabusId(id)
                                .stream()
                                .map(chapter -> SyllabusDetailDTO.ChapterInSyllabus.builder()
                                        .chapterId(chapter.getId())
                                        .chapterName(chapter.getChapterName())
                                        .orderNumber(chapter.getOrderNumber())
                                        .build())
                                .collect(Collectors.toList()));
                syllabusDetailDTO.setLessonListInSyllabus(
                        syllabusRepository.findLessonsBySyllabusId(id)
                                .stream()
                                .map(lesson -> SyllabusDetailDTO.LessonInSyllabus.builder()
                                        .id(lesson.getId())
                                        .lessonName(lesson.getLessonName())
                                        .content(lesson.getContent())
                                        .orderNumber(lesson.getOrderNumber())
                                        .chapter(SyllabusDetailDTO.ChapterInSyllabus.builder()
                                                .chapterId(lesson.getChapter().getId())
                                                .chapterName(lesson.getChapter().getChapterName())
                                                .orderNumber(lesson.getChapter().getOrderNumber())
                                                .build())
                                        .build())
                                .collect(Collectors.toList()));
                break;
            default:
                throw new ApiException("Invalid include parameter. Use CHAPTERS, LESSONS, or ALL", HttpStatus.BAD_REQUEST.value());
        }

        return syllabusDetailDTO;
    }

    @Override
    public DataResponse<List<SyllabusDTO>> getSyllabusList(int page, int size, String searchText) {
        Page<Syllabus> syllabusPage = syllabusRepository.findBySearchText(searchText, PageRequest.of(page, size, Sort.by("createdAt").descending()));
        List<SyllabusDTO> responses = syllabusPage.getContent().stream()
                .map(syllabusMapper::toSyllabusDTO)
                .collect(Collectors.toList());

        return DataResponse.<List<SyllabusDTO>>builder()
                .traceId(TraceUtil.getTraceId())
                .success(true)
                .message("Successful")
                .data(responses)
                .timestamp(java.time.LocalDateTime.now())
                .page(page)
                .size(size)
                .totalElements(syllabusPage.getTotalElements())
                .totalPages(syllabusPage.getTotalPages())
                .build();
    }

    @Override
    public byte[] generateSyllabusImportTemplate() {
        return fileService.generateSyllabusImportTemplate();
    }

    @Override
    public String getSyllabusTemplateSasUrl() {
        return blobSasService.generateSasUrl(syllabusTemplate, Duration.ofMinutes(30));
    }

    @Override
    @Transactional
    public List<SyllabusDTO> importSyllabusFromExcel(MultipartFile file) {
        // Đọc dữ liệu từ file Excel
        List<ImportSyllabusDTO> importList = fileService.readExcelData(file, "Import Data", ImportSyllabusDTO.class);
        List<SyllabusDTO> result = new ArrayList<>();

        for (ImportSyllabusDTO record : importList) {
            // 1. Kiểm tra các trường bắt buộc
            if (StringUtils.isBlank(record.getSyllabusName()) || record.getSyllabusName().length() > 100) {
                throw new ApiException("Invalid syllabusName: " + record.getSyllabusName() + ". Must be non-empty and max 100 characters.", HttpStatus.BAD_REQUEST.value());
            }

            if (StringUtils.isBlank(record.getLevelCode())) {
                throw new ApiException("Level code is required", HttpStatus.BAD_REQUEST.value());
            }

            // 2. Kiểm tra levelCode tồn tại
            Level level = levelRepository.findByLevelCodeIgnoreCase(record.getLevelCode())
                    .orElseThrow(() -> new ApiException(
                            "Level not found with code: " + record.getLevelCode(),
                            HttpStatus.NOT_FOUND.value()
                    ));

            // 4. Kiểm tra syllabusName không trùng
            if (syllabusRepository.existsBySyllabusName(record.getSyllabusName())) {
                throw new ApiException("Syllabus name already exists: " + record.getSyllabusName(), HttpStatus.CONFLICT.value());
            }

            // 5. Tạo CreateSyllabusRequest
            CreateSyllabusRequest request = new CreateSyllabusRequest();
            request.setSyllabusName(record.getSyllabusName());
            request.setLevelId(level.getId());
            request.setDescription(record.getDescription());

            // 6. Gọi createSyllabus
            SyllabusDTO syllabusDTO = createSyllabus(request);

            // 8. Thêm vào kết quả
            result.add(syllabusDTO);
        }

        log.info("Imported {} syllabuses successfully", result.size());

        // notify actor
        try {
            Long actor = jwtUtil.extractUserIdFromCurrentRequest();
            String title = "Import syllabus hoàn tất";
            String message = "Bạn đã import " + result.size() + " syllabus thành công.";
            notificationService.createNotification(actor, null, title, message, null, null);
        } catch (Exception ex) {
            log.debug("Failed to send importSyllabus notification: {}", ex.getMessage());
        }

        return result;
    }

    @Override
    public byte[] exportSyllabuses(List<Long> ids) {
        List<Syllabus> syllabuses;

        // Case 1: ids = null hoặc không truyền → Export tất cả
        if (ids == null || ids.isEmpty()) {
            syllabuses = syllabusRepository.findAll().stream()
                    .filter(s -> s.getDeletedAt() == null)
                    .collect(Collectors.toList());

            if (syllabuses.isEmpty()) {
                throw new ApiException(
                        "No syllabuses found in the system",
                        HttpStatus.NOT_FOUND.value()
                );
            }
        }
        // Case 2: ids có giá trị → Export theo IDs
        else {
            // Validation: Không cho chứa null values trong list
            if (ids.contains(null)) {
                throw new ApiException(
                        "IDs list cannot contain null values",
                        HttpStatus.BAD_REQUEST.value()
                );
            }

            // Validation: Không cho duplicate IDs
            Set<Long> uniqueIds = new HashSet<>(ids);
            if (uniqueIds.size() != ids.size()) {
                List<Long> duplicates = ids.stream()
                        .filter(id -> Collections.frequency(ids, id) > 1)
                        .distinct()
                        .collect(Collectors.toList());
                throw new ApiException(
                        "Duplicate IDs found: " + duplicates,
                        HttpStatus.BAD_REQUEST.value()
                );
            }

            // Validation: Kiểm tra IDs phải là số dương
            List<Long> invalidIds = ids.stream()
                    .filter(id -> id <= 0)
                    .collect(Collectors.toList());
            if (!invalidIds.isEmpty()) {
                throw new ApiException(
                        "Invalid IDs (must be positive): " + invalidIds,
                        HttpStatus.BAD_REQUEST.value()
                );
            }

            // Lấy syllabuses từ DB
            syllabuses = syllabusRepository.findAllById(ids).stream()
                    .filter(s -> s.getDeletedAt() == null)
                    .collect(Collectors.toList());

            // Validation: Check syllabuses không tồn tại hoặc đã bị xóa
            if (syllabuses.isEmpty()) {
                throw new ApiException(
                        "No syllabuses found with provided IDs or all are deleted",
                        HttpStatus.NOT_FOUND.value()
                );
            }

            // Validation: Check có IDs nào không tồn tại
            if (syllabuses.size() != ids.size()) {
                List<Long> foundIds = syllabuses.stream()
                        .map(Syllabus::getId)
                        .collect(Collectors.toList());
                List<Long> missingIds = ids.stream()
                        .filter(id -> !foundIds.contains(id))
                        .collect(Collectors.toList());
                throw new ApiException(
                        "Syllabuses not found or deleted for IDs: " + missingIds,
                        HttpStatus.NOT_FOUND.value()
                );
            }
        }

        // Convert sang ExportSyllabusDTO
        List<ExportSyllabusDTO> exportData = syllabuses.stream()
                .map(this::convertToExportSyllabusDTO)
                .collect(Collectors.toList());

        // Tạo summary info
        Map<String, String> summaryInfo = createSummaryInfo(exportData, ids);

        // Export
        return fileService.exportSyllabusesData(exportData, summaryInfo);
    }

    private Map<String, String> createSummaryInfo(List<ExportSyllabusDTO> exportData, List<Long> ids) {
        Map<String, String> summaryInfo = new LinkedHashMap<>();

        summaryInfo.put("Ngày xuất", new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date()));
        summaryInfo.put("Người xuất", jwtUtil.extractUsernameFromCurrentRequest());
        summaryInfo.put("Tổng số syllabus", String.valueOf(exportData.size()));

        // Tính toán thống kê
        int totalChapters = exportData.stream()
                .mapToInt(s -> s.getTotalChapters() != null ? s.getTotalChapters() : 0)
                .sum();
        int totalLessons = exportData.stream()
                .mapToInt(s -> s.getTotalLessons() != null ? s.getTotalLessons() : 0)
                .sum();

        summaryInfo.put("Tổng số chapter", String.valueOf(totalChapters));
        summaryInfo.put("Tổng số lesson", String.valueOf(totalLessons));

        // Thống kê theo level
        Map<String, Long> levelStats = exportData.stream()
                .collect(Collectors.groupingBy(
                        ExportSyllabusDTO::getLevelName,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
        summaryInfo.put("Phân bổ theo Level",
                levelStats.entrySet().stream()
                        .map(e -> e.getKey() + " (" + e.getValue() + ")")
                        .collect(Collectors.joining(", "))
        );

        // Thêm thông tin export
        if (ids == null || ids.isEmpty()) {
            summaryInfo.put("Loại export", "Toàn bộ Syllabus");
        } else {
            summaryInfo.put("Loại export", "Theo IDs đã chọn");
            summaryInfo.put("Danh sách IDs", ids.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "))
            );
        }

        return summaryInfo;
    }

    private ExportSyllabusDTO convertToExportSyllabusDTO(Syllabus syllabus) {
        SimpleDateFormat dateTimeFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");

        // Lấy chapters
        List<Chapter> chapters = syllabusRepository.findChaptersBySyllabusId(syllabus.getId());

        // Convert chapters với lessons
        List<ExportSyllabusDTO.ChapterInfo> chapterInfos = chapters.stream()
                .map(chapter -> {
                    List<Lesson> lessons = chapter.getLessons().stream()
                            .filter(l -> l.getDeletedAt() == null)
                            .sorted(Comparator.comparing(Lesson::getOrderNumber))
                            .collect(Collectors.toList());

                    List<ExportSyllabusDTO.LessonInfo> lessonInfos = lessons.stream()
                            .map(lesson -> ExportSyllabusDTO.LessonInfo.builder()
                                    .lessonName(lesson.getLessonName())
                                    .content(lesson.getContent())
                                    .orderNumber(lesson.getOrderNumber())
                                    .build())
                            .collect(Collectors.toList());

                    return ExportSyllabusDTO.ChapterInfo.builder()
                            .chapterCode(chapter.getChapterCode())
                            .chapterName(chapter.getChapterName())
                            .orderNumber(chapter.getOrderNumber())
                            .lessonCount(lessons.size())
                            .lessons(lessonInfos)
                            .build();
                })
                .collect(Collectors.toList());

        int totalLessons = chapterInfos.stream()
                .mapToInt(c -> c.getLessonCount() != null ? c.getLessonCount() : 0)
                .sum();

        return ExportSyllabusDTO.builder()
                .syllabusCode(syllabus.getSyllabusCode())
                .syllabusName(syllabus.getSyllabusName())
                .levelCode(syllabus.getLevel().getLevelCode())
                .levelName(syllabus.getLevel().getLevelName())
                .description(syllabus.getDescription())
                .createdAt(syllabus.getCreatedAt() != null
                        ? dateTimeFormat.format(Date.from(syllabus.getCreatedAt().toInstant()))
                        : "")
                .createdBy(syllabus.getCreatedBy())
                .totalChapters(chapters.size())
                .totalLessons(totalLessons)
                .chapters(chapterInfos)
                .build();
    }

    @Override
    public byte[] validateSyllabusImportFile(MultipartFile file) {
        ValidationResult<ImportSyllabusDTO> result = new ValidationResult<>();
        List<ImportSyllabusDTO> importList;

        // Bước 1: Đọc file - catch lỗi format
        try {
            importList = fileService.readExcelData(file, "Import Data", ImportSyllabusDTO.class);
            result.setTotalRows(importList.size());

            if (importList.isEmpty()) {
                throw new ApiException("Import file is empty", HttpStatus.BAD_REQUEST.value());
            }
        } catch (ApiException e) {
            // Lỗi khi đọc file
            result.setTotalRows(0);
            result.setValidRows(0);
            result.setInvalidRows(0);

            ValidationResult.ValidatedRow<ImportSyllabusDTO> errorRow =
                    new ValidationResult.ValidatedRow<>();
            errorRow.setData(new ImportSyllabusDTO());
            errorRow.setRowNumber(0);
            errorRow.setValid(false);
            errorRow.setErrorMessage("❌ LỖI ĐỌC FILE:\n" + e.getMessage());
            result.addRow(errorRow);

            return fileService.generateValidationResultFile(
                    file, "Import Data", result, ImportSyllabusDTO.class
            );
        }

        // Bước 2: Fetch all level codes một lần
        Set<String> levelCodes = importList.stream()
                .filter(record -> record.getLevelCode() != null && !record.getLevelCode().isBlank())
                .map(record -> record.getLevelCode().toLowerCase().trim())
                .collect(Collectors.toSet());

        List<Level> levels = levelRepository.findByLevelCodeInIgnoreCase(new ArrayList<>(levelCodes));

        Map<String, Level> levelMap = levels.stream()
                .collect(Collectors.toMap(
                        l -> l.getLevelCode().toLowerCase(),
                        l -> l
                ));

        // Bước 3: Validate từng row
        int validCount = 0;
        int invalidCount = 0;

        // Track duplicate syllabus names trong file
        Set<String> seenSyllabusNames = new HashSet<>();

        for (int i = 0; i < importList.size(); i++) {
            ImportSyllabusDTO record = importList.get(i);
            ValidationResult.ValidatedRow<ImportSyllabusDTO> validatedRow =
                    new ValidationResult.ValidatedRow<>();
            validatedRow.setData(record);
            validatedRow.setRowNumber(i + 2); // +2 vì header ở row 1

            StringBuilder errors = new StringBuilder();

            try {
                // Validate Syllabus Name
                if (StringUtils.isBlank(record.getSyllabusName())) {
                    errors.append("• Syllabus Name không được để trống\n");
                } else {
                    if (record.getSyllabusName().length() > 100) {
                        errors.append("• Syllabus Name không được vượt quá 100 ký tự\n");
                    }

                    // Check duplicate trong file
                    String syllabusNameLower = record.getSyllabusName().toLowerCase().trim();
                    if (seenSyllabusNames.contains(syllabusNameLower)) {
                        errors.append("• Syllabus Name bị trùng lặp trong file: ")
                                .append(record.getSyllabusName()).append("\n");
                    } else {
                        seenSyllabusNames.add(syllabusNameLower);

                        // Check duplicate trong DB
                        if (syllabusRepository.existsBySyllabusNameIgnoreCase(record.getSyllabusName())) {
                            errors.append("• Syllabus Name đã tồn tại trong hệ thống: ")
                                    .append(record.getSyllabusName()).append("\n");
                        }
                    }
                }

                // Validate Level Code
                if (StringUtils.isBlank(record.getLevelCode())) {
                    errors.append("• Level Code không được để trống\n");
                } else {
                    String levelCodeLower = record.getLevelCode().toLowerCase().trim();
                    Level level = levelMap.get(levelCodeLower);

                    if (level == null) {
                        errors.append("• Level Code không tồn tại trong hệ thống: ")
                                .append(record.getLevelCode()).append("\n");
                    }
                }

                // Validate Description (optional, nhưng nếu có thì check length)
                if (record.getDescription() != null && record.getDescription().length() > 1000) {
                    errors.append("• Description không được vượt quá 1000 ký tự\n");
                }

                if (errors.length() > 0) {
                    validatedRow.setValid(false);
                    validatedRow.setErrorMessage(errors.toString().trim());
                    invalidCount++;
                } else {
                    validatedRow.setValid(true);
                    validatedRow.setErrorMessage("✓ Hợp lệ");
                    validCount++;
                }

            } catch (Exception e) {
                validatedRow.setValid(false);
                validatedRow.setErrorMessage("⚠️ Lỗi xử lý dòng: " + e.getMessage());
                invalidCount++;
            }

            result.addRow(validatedRow);
        }

        result.setValidRows(validCount);
        result.setInvalidRows(invalidCount);

        return fileService.generateValidationResultFile(
                file, "Import Data", result, ImportSyllabusDTO.class
        );
    }
}
