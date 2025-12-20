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
        final String method = "createSyllabus";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} syllabusName={}", method, traceId, request.getSyllabusName());

        String syllabusNameNormalized = DataUtil.normalize(request.getSyllabusName());
        if (syllabusRepository.existsBySyllabusNameIgnoreCase(syllabusNameNormalized)) {
            log.error("[{}] traceId={} duplicate syllabus name: {}", method, traceId, syllabusNameNormalized);
            throw new ApiException(Const.SYLLABUS.EXIST_NAME, HttpStatus.BAD_REQUEST.value());
        }

        Level level = levelRepository.findByIdAndDeletedAtIsNull(request.getLevelId())
                .orElseThrow(() -> {
                    log.error("[{}] traceId={} level not found id={}", method, traceId, request.getLevelId());
                    return new ApiException(Const.SYLLABUS.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        Syllabus syllabus = syllabusMapper.toSyllabus(request);
        syllabus.setSyllabusName(syllabusNameNormalized);
        syllabus.setLevel(level);
        syllabusRepository.save(syllabus);

        String syllabusCode = DataUtil.generateSyllabusCode(syllabus.getId());
        syllabus.setSyllabusCode(syllabusCode);
        syllabusRepository.saveAndFlush(syllabus);
        log.info("[{}] traceId={} created syllabus id={} code={}", method, traceId, syllabus.getId(), syllabusCode);

        // notify actor
        try {
            Long actor = jwtUtil.extractUserIdFromCurrentRequest();
            String title = "Syllabus created successfully";
            String message = "You have created the syllabus \"" + syllabus.getSyllabusName() + "\".";
            String url = "/manager/syllabuses/" + syllabus.getId() + "/chapters";

            notificationService.createNotifications(actor, null, title, message, url, null);
            log.debug("[{}] traceId={} notification sent for created syllabus id={}", method, traceId, syllabus.getId());
        } catch (Exception ex) {
            log.debug("[{}] traceId={} Failed to send createSyllabus notification: {}", method, traceId, ex.getMessage());
        }

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} createdSyllabusId={} durationMs={}", method, traceId, syllabus.getId(), durationMs);
        return syllabusMapper.toSyllabusDTO(syllabus);
    }

    @Override
    @Transactional
    public SyllabusDTO updateSyllabus(Long id, UpdateSyllabusRequest request) {
        final String method = "updateSyllabus";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} syllabusId={} syllabusName={}", method, traceId, id, request.getSyllabusName());

        String syllabusNameNormalized = DataUtil.normalize(request.getSyllabusName());
        if (syllabusRepository.existsBySyllabusNameIgnoreCaseAndIdNotAndDeletedAtIsNull(syllabusNameNormalized, id)) {
            log.error("[{}] traceId={} duplicate syllabus name for update: {}", method, traceId, syllabusNameNormalized);
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
        log.info("[{}] traceId={} updated syllabus id={}", method, traceId, id);

        // notify actor
        try {
            Long actor = jwtUtil.extractUserIdFromCurrentRequest();
            String title = "Syllabus updated successfully";
            String message = "You have updated the syllabus \"" + syllabus.getSyllabusName() + "\".";

            String url = "/manager/syllabuses/";

            notificationService.createNotifications(actor, null, title, message, url, null);
            log.debug("[{}] traceId={} notification sent for updated syllabus id={}", method, traceId, id);
        } catch (Exception ex) {
            log.debug("[{}] traceId={} Failed to send updateSyllabus notification: {}", method, traceId, ex.getMessage());
        }

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} updatedSyllabusId={} durationMs={}", method, traceId, id, durationMs);
        return syllabusMapper.toSyllabusDTO(syllabus);
    }

    @Override
    @Transactional
    public void deleteSyllabus(Long id) {
        final String method = "deleteSyllabus";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} syllabusId={}", method, traceId, id);

        Syllabus syllabus = syllabusRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.error("[{}] traceId={} syllabus not found id={}", method, traceId, id);
                    return new ApiException(Const.SYLLABUS.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        boolean hasActiveClasses = classRepository.existsBySyllabusIdAndStatusNotAndDeletedAtIsNull(
                id, ClassStatus.FINISHED);

        if (hasActiveClasses) {
            log.error("[{}] traceId={} cannot delete syllabus in use id={}", method, traceId, id);
            throw new ApiException(Const.SYLLABUS.IN_USE_BY_ACTIVE_CLASS, HttpStatus.BAD_REQUEST.value());
        }

        syllabus.setDeletedBy(jwtUtil.extractUsernameFromCurrentRequest());
        syllabus.setDeletedAt(OffsetDateTime.now());
        syllabusRepository.save(syllabus);
        log.info("[{}] traceId={} deleted syllabus id={}", method, traceId, id);

        // notify actor
        try {
            Long actor = jwtUtil.extractUserIdFromCurrentRequest();
            String title = "Syllabus deleted";
            String message = "You have deleted the syllabus \"" + syllabus.getSyllabusName() + "\".";

            String url = "/manager/syllabuses/";

            notificationService.createNotifications(actor, null, title, message, url, null);
            log.debug("[{}] traceId={} notification sent for deleted syllabus id={}", method, traceId, id);
        } catch (Exception ex) {
            log.debug("[{}] traceId={} Failed to send deleteSyllabus notification: {}", method, traceId, ex.getMessage());
        }

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} deletedSyllabusId={} durationMs={}", method, traceId, id, durationMs);
    }

    @Override
    public SyllabusDetailDTO getSyllabusDetail(Long id, String include) {
        final String method = "getSyllabusDetail";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} syllabusId={} include={}", method, traceId, id, include);

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
                log.error("[{}] traceId={} invalid include param: {}", method, traceId, include);
                throw new ApiException(Const.SYLLABUS.INVALID_INCLUDE_PARAM, HttpStatus.BAD_REQUEST.value());
        }

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} syllabusId={} durationMs={}", method, traceId, id, durationMs);
        return syllabusDetailDTO;
    }

    @Override
    public DataResponse<List<SyllabusDTO>> getSyllabusList(int page, int size, String searchText) {
        final String method = "getSyllabusList";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} page={} size={} searchText={}", method, traceId, page, size, searchText);

        Page<Syllabus> syllabusPage = syllabusRepository.findBySearchText(searchText, PageRequest.of(page, size, Sort.by("createdAt").descending()));
        List<SyllabusDTO> responses = syllabusPage.getContent().stream()
                .map(syllabusMapper::toSyllabusDTO)
                .collect(Collectors.toList());

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} page={} size={} durationMs={}", method, traceId, page, size, durationMs);

        return DataResponse.<List<SyllabusDTO>>builder()
                .traceId(TraceUtil.getTraceId())
                .success(true)
                .message(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
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
        final String method = "generateSyllabusImportTemplate";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={}", method, traceId);

        byte[] template = fileService.generateSyllabusImportTemplate();

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} templateSizeBytes={} durationMs={}", method, traceId, template != null ? template.length : 0, durationMs);
        return template;
    }

    @Override
    public String getSyllabusTemplateSasUrl() {
        final String method = "getSyllabusTemplateSasUrl";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={}", method, traceId);

        String url = blobSasService.generateSasUrl(syllabusTemplate, Duration.ofMinutes(30));
        log.debug("[{}] traceId={} generated sas url", method, traceId);

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} durationMs={}", method, traceId, durationMs);
        return url;
    }

    @Override
    @Transactional
    public List<SyllabusDTO> importSyllabusFromExcel(MultipartFile file) {
        final String method = "importSyllabusFromExcel";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} filePresent={}", method, traceId, file != null && !file.isEmpty());

        // Read input
        List<ImportSyllabusDTO> importList = fileService.readExcelData(file, "Import Data", ImportSyllabusDTO.class);
        List<SyllabusDTO> result = new ArrayList<>();

        for (ImportSyllabusDTO record : importList) {
            String name = record.getSyllabusName();

            if (name == null) {
                log.error("[{}] traceId={} import validation failed missing name", method, traceId);
                throw new ApiException(Const.SYLLABUS.IMPORT_NAME_REQUIRED, HttpStatus.BAD_REQUEST.value());
            }

            if (name.isBlank()) {
                log.error("[{}] traceId={} import validation failed blank name", method, traceId);
                throw new ApiException(Const.SYLLABUS.IMPORT_NAME_EMPTY, HttpStatus.BAD_REQUEST.value());
            }

            if (name.length() > 100) {
                log.error("[{}] traceId={} import validation failed name too long", method, traceId);
                throw new ApiException(Const.SYLLABUS.IMPORT_NAME_TOO_LONG, HttpStatus.BAD_REQUEST.value());
            }

            if (StringUtils.isBlank(record.getLevelCode())) {
                log.error("[{}] traceId={} import validation failed missing level code", method, traceId);
                throw new ApiException(Const.SYLLABUS.IMPORT_LEVEL_CODE_REQUIRED, HttpStatus.BAD_REQUEST.value());
            }

            Level level = levelRepository.findByLevelCodeIgnoreCase(record.getLevelCode())
                    .orElseThrow(() -> {
                        log.error("[{}] traceId={} level not found code={}", method, traceId, record.getLevelCode());
                        return new ApiException(String.format(Const.SYLLABUS.IMPORT_LEVEL_NOT_FOUND, record.getLevelCode()), HttpStatus.NOT_FOUND.value());
                    });

            if (syllabusRepository.existsBySyllabusName(record.getSyllabusName())) {
                log.error("[{}] traceId={} syllabus name already exists name={}", method, traceId, record.getSyllabusName());
                throw new ApiException(String.format(Const.SYLLABUS.IMPORT_SYLLABUS_ALREADY_EXISTS, record.getSyllabusName()), HttpStatus.CONFLICT.value());
            }

            CreateSyllabusRequest request = new CreateSyllabusRequest();
            request.setSyllabusName(record.getSyllabusName());
            request.setLevelId(level.getId());
            request.setDescription(record.getDescription());

            SyllabusDTO syllabusDTO = createSyllabus(request);
            result.add(syllabusDTO);
        }

        log.info("[{}] traceId={} importedCount={}", method, traceId, result.size());

        // notify actor
        try {
            Long actor = jwtUtil.extractUserIdFromCurrentRequest();
            String title = "Import syllabus successfully";
            String message = "You have successfully imported " + result.size() + " syllabus.";
            String url = "/manager/syllabuses/";
            notificationService.createNotifications(actor, null, title, message, url, null);
            log.debug("[{}] traceId={} import notification sent count={}", method, traceId, result.size());
        } catch (Exception ex) {
            log.debug("[{}] traceId={} Failed to send importSyllabus notification: {}", method, traceId, ex.getMessage());
        }

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} importedCount={} durationMs={}", method, traceId, result.size(), durationMs);
        return result;
    }

    @Override
    public byte[] exportSyllabuses(List<Long> ids) {
        final String method = "exportSyllabuses";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} idsPresent={}", method, traceId, ids != null && !ids.isEmpty());

        List<Syllabus> syllabuses;

        if (ids == null || ids.isEmpty()) {
            syllabuses = syllabusRepository.findAll().stream()
                    .filter(s -> s.getDeletedAt() == null)
                    .collect(Collectors.toList());

            if (syllabuses.isEmpty()) {
                log.error("[{}] traceId={} no syllabuses found for export", method, traceId);
                throw new ApiException(Const.SYLLABUS.EXPORT_NO_SYLLABUSES_FOUND, HttpStatus.NOT_FOUND.value());
            }
        } else {
            if (ids.contains(null)) {
                log.error("[{}] traceId={} ids list contains null", method, traceId);
                throw new ApiException(Const.SYLLABUS.EXPORT_IDS_CONTAIN_NULL, HttpStatus.BAD_REQUEST.value());
            }

            Set<Long> uniqueIds = new HashSet<>(ids);
            if (uniqueIds.size() != ids.size()) {
                List<Long> duplicates = ids.stream()
                        .filter(id -> Collections.frequency(ids, id) > 1)
                        .distinct()
                        .collect(Collectors.toList());
                log.error("[{}] traceId={} duplicate ids in request: {}", method, traceId, duplicates);
                throw new ApiException(String.format(Const.SYLLABUS.EXPORT_DUPLICATE_IDS, duplicates), HttpStatus.BAD_REQUEST.value());
            }

            List<Long> invalidIds = ids.stream()
                    .filter(id -> id <= 0)
                    .collect(Collectors.toList());
            if (!invalidIds.isEmpty()) {
                log.error("[{}] traceId={} invalid ids: {}", method, traceId, invalidIds);
                throw new ApiException(String.format(Const.SYLLABUS.EXPORT_INVALID_IDS, invalidIds), HttpStatus.BAD_REQUEST.value());
            }

            syllabuses = syllabusRepository.findAllById(ids).stream()
                    .filter(s -> s.getDeletedAt() == null)
                    .collect(Collectors.toList());

            if (syllabuses.isEmpty()) {
                log.error("[{}] traceId={} no syllabuses found with provided ids", method, traceId);
                throw new ApiException(Const.SYLLABUS.EXPORT_NO_MATCHING_IDS, HttpStatus.NOT_FOUND.value());
            }

            if (syllabuses.size() != ids.size()) {
                List<Long> foundIds = syllabuses.stream().map(Syllabus::getId).collect(Collectors.toList());
                List<Long> missingIds = ids.stream().filter(id -> !foundIds.contains(id)).collect(Collectors.toList());
                log.error("[{}] traceId={} missing ids: {}", method, traceId, missingIds);
                throw new ApiException(String.format(Const.SYLLABUS.EXPORT_NOT_FOUND_OR_DELETED_FOR_IDS, missingIds), HttpStatus.NOT_FOUND.value());
            }
        }

        List<ExportSyllabusDTO> exportData = syllabuses.stream()
                .map(this::convertToExportSyllabusDTO)
                .collect(Collectors.toList());

        Map<String, String> summaryInfo = createSummaryInfo(exportData, ids);

        byte[] file = fileService.exportSyllabusesData(exportData, summaryInfo);
        log.info("[{}] exit traceId={} exportFileSizeBytes={}", method, traceId, file != null ? file.length : 0);
        return file;
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
    public byte[] downloadSyllabusValidationFile(MultipartFile file) {
        final String method = "downloadSyllabusValidationFile";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} filePresent={}", method, traceId, file != null && !file.isEmpty());

        ValidationResult<ImportSyllabusDTO> result = new ValidationResult<>();
        List<ImportSyllabusDTO> importList;

        // Bước 1: Đọc file - catch lỗi format
        try {
            importList = fileService.readExcelData(file, "Import Data", ImportSyllabusDTO.class);
            result.setTotalRows(importList.size());

            if (importList.isEmpty()) {
                log.error("[{}] traceId={} import file empty", method, traceId);
                throw new ApiException(Const.SYLLABUS.IMPORT_FILE_EMPTY, HttpStatus.BAD_REQUEST.value());
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
