package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
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

    @Value("${azure.storage.syllabus-template}")
    private String syllabusTemplate;

    @Override
    @Transactional
    public SyllabusDTO createSyllabus(CreateSyllabusRequest request) {

        if (syllabusRepository.existsBySyllabusNameIgnoreCase(request.getSyllabusName())) {
            throw new ApiException("Syllabus name already exists", HttpStatus.BAD_REQUEST.value());
        }

        Level level = levelRepository.findById(request.getLevelId())
                .orElseThrow(() -> new ApiException("Level not found", HttpStatus.NOT_FOUND.value()));

        Syllabus syllabus = syllabusMapper.toSyllabus(request);
        syllabus.setLevel(level);
        syllabusRepository.save(syllabus);

        String syllabusCode = DataUtil.generateSyllabusCode(syllabus.getId());
        syllabus.setSyllabusCode(syllabusCode);
        syllabusRepository.saveAndFlush(syllabus);

        return syllabusMapper.toSyllabusDTO(syllabus);
    }

    @Override
    @Transactional
    public SyllabusDTO updateSyllabus(Long id, UpdateSyllabusRequest request) {
        Syllabus syllabus = syllabusRepository.findById(id)
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Syllabus not found or deleted", HttpStatus.NOT_FOUND.value()));

        Level level = levelRepository.findById(request.getLevelId())
                .orElseThrow(() -> new ApiException("Level not found", HttpStatus.NOT_FOUND.value()));

        Syllabus updatedSyllabus = syllabusMapper.toSyllabus(request);
        syllabus.setSyllabusName(updatedSyllabus.getSyllabusName());
        syllabus.setLevel(level);
        syllabus.setDescription(updatedSyllabus.getDescription());
        syllabusRepository.save(syllabus);

        return syllabusMapper.toSyllabusDTO(syllabus);
    }

    @Override
    @Transactional
    public void deleteSyllabus(Long id) {
        Syllabus syllabus = syllabusRepository.findById(id)
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Syllabus not found or deleted", HttpStatus.NOT_FOUND.value()));

        syllabus.setDeletedBy(jwtUtil.extractUsernameFromCurrentRequest());
        syllabus.setDeletedAt(OffsetDateTime.now());
        syllabusRepository.save(syllabus);
    }

    @Override
    public SyllabusDetailDTO getSyllabusDetail(Long id, String include) {
        Syllabus syllabus = syllabusRepository.findById(id)
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Syllabus not found or deleted", HttpStatus.NOT_FOUND.value()));

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
        return result;
    }

    @Override
    public byte[] exportAllSyllabuses(String searchText) {
        // Lấy tất cả syllabuses
        List<Syllabus> syllabuses = syllabusRepository.findAllBySearchText(searchText);

        // Convert sang ExportSyllabusDTO
        List<ExportSyllabusDTO> exportData = syllabuses.stream()
                .map(this::convertToExportSyllabusDTO)
                .collect(Collectors.toList());

        // Tạo summary info
        Map<String, String> summaryInfo = new LinkedHashMap<>();
        summaryInfo.put("Ngày xuất", new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date()));
        summaryInfo.put("Tổng số syllabus", String.valueOf(exportData.size()));

        int totalChapters = exportData.stream()
                .mapToInt(s -> s.getTotalChapters() != null ? s.getTotalChapters() : 0)
                .sum();
        int totalLessons = exportData.stream()
                .mapToInt(s -> s.getTotalLessons() != null ? s.getTotalLessons() : 0)
                .sum();

        summaryInfo.put("Tổng số chapter", String.valueOf(totalChapters));
        summaryInfo.put("Tổng số lesson", String.valueOf(totalLessons));

        if (searchText != null && !searchText.trim().isEmpty()) {
            summaryInfo.put("Từ khóa tìm kiếm", searchText);
        }

        // Export
        return fileService.exportSyllabusesData(exportData, summaryInfo);
    }

    @Override
    public byte[] exportSyllabusDetail(Long syllabusId) {
        // Kiểm tra syllabus tồn tại
        Syllabus syllabus = syllabusRepository.findById(syllabusId)
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Syllabus not found or deleted", HttpStatus.NOT_FOUND.value()));

        // Convert sang ExportSyllabusDTO với đầy đủ thông tin
        ExportSyllabusDTO exportData = convertToExportSyllabusDTO(syllabus);

        // Tạo summary info
        Map<String, String> summaryInfo = new LinkedHashMap<>();
        summaryInfo.put("Ngày xuất", new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date()));
        summaryInfo.put("Mã Syllabus", syllabus.getSyllabusCode());
        summaryInfo.put("Tên Syllabus", syllabus.getSyllabusName());
        summaryInfo.put("Level", syllabus.getLevel().getLevelName() + " (" + syllabus.getLevel().getLevelCode() + ")");
        summaryInfo.put("Tổng Chapter", String.valueOf(exportData.getTotalChapters()));
        summaryInfo.put("Tổng Lesson", String.valueOf(exportData.getTotalLessons()));

        // Export chi tiết
        return fileService.exportSyllabusDetailData(exportData, summaryInfo);
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
}