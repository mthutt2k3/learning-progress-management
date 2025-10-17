package com.learning.progress.service.impl;

import com.learning.progress.dto.SyllabusDTO;
import com.learning.progress.dto.SyllabusDetailDTO;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.dto.syllabus.CreateSyllabusRequest;
import com.learning.progress.dto.syllabus.ImportSyllabusDTO;
import com.learning.progress.dto.syllabus.UpdateSyllabusRequest;
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
                .traceId(org.slf4j.MDC.get("traceId"))
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

//    @Override
//    @Transactional
//    public List<SyllabusDTO> importSyllabusFromExcel(MultipartFile file) {
//        List<ImportSyllabusDTO> importList = fileService.readExcelData(file, "Import Data", ImportSyllabusDTO.class);
//        List<SyllabusDTO> result = new ArrayList<>();
//        String currentUser = jwtUtil.extractUsernameFromCurrentRequest();
//        OffsetDateTime now = OffsetDateTime.now();
//
//        // Nhóm theo syllabusCode
//        Map<String, List<ImportSyllabusDTO>> chaptersBySyllabus = importList.stream()
//                .collect(Collectors.groupingBy(ImportChapterDTO::getSyllabusCode));
//
//        for (Map.Entry<String, List<ImportChapterDTO>> entry : chaptersBySyllabus.entrySet()) {
//            String syllabusCode = entry.getKey();
//            List<ImportChapterDTO> chapters = entry.getValue();
//
//            // 1. Kiểm tra syllabusCode
//            Syllabus syllabus = syllabusRepository.findBySyllabusCode(syllabusCode)
//                    .filter(s -> s.getDeletedAt() == null)
//                    .orElseThrow(() -> new ApiException("Syllabus không tìm thấy hoặc đã bị xóa với mã: " + syllabusCode, HttpStatus.NOT_FOUND.value()));
//
//            // 2. Validate chapters
//            for (ImportChapterDTO req : chapters) {
//                if (req.getChapterName() == null || req.getChapterName().trim().isEmpty()) {
//                    throw new ApiException("Chapter name là bắt buộc: " + req.getChapterName(), HttpStatus.BAD_REQUEST.value());
//                }
//                if (req.getChapterName().length() > 255) {
//                    throw new ApiException("Chapter name vượt quá 255 ký tự: " + req.getChapterName(), HttpStatus.BAD_REQUEST.value());
//                }
//                if (req.getOrderNumber() == null || req.getOrderNumber() < 1) {
//                    throw new ApiException("Order number phải là số dương: " + req.getOrderNumber(), HttpStatus.BAD_REQUEST.value());
//                }
//            }
//
//            // 3. Validate order numbers
//            Set<Integer> orderNumbers = chapters.stream()
//                    .map(ImportChapterDTO::getOrderNumber)
//                    .filter(Objects::nonNull)
//                    .collect(Collectors.toSet());
//
//            int chapterSize = chapters.size();
//            Set<Integer> expectedOrders = IntStream.rangeClosed(1, chapterSize).boxed().collect(Collectors.toSet());
//
//            if (orderNumbers.size() != chapterSize || !orderNumbers.equals(expectedOrders)) {
//                throw new ApiException(
//                        String.format("Order numbers phải tuần tự từ 1 đến %d, không trùng lặp và không có gap. Current: %s",
//                                chapterSize, orderNumbers),
//                        HttpStatus.BAD_REQUEST.value()
//                );
//            }
//
//            log.info("Validation passed for syllabusCode={}: total chapters={}", syllabusCode, chapterSize);
//
//            // 4. Tạo mới chapters
//            for (ImportChapterDTO req : chapters) {
//                Chapter newChapter = new Chapter();
//                newChapter.setSyllabus(syllabus);
//                newChapter.setChapterName(req.getChapterName());
//                newChapter.setOrderNumber(req.getOrderNumber());
//                newChapter.setCreatedBy(currentUser);
//                newChapter.setUpdatedBy(currentUser);
//                newChapter.setUpdatedAt(now);
//                Chapter saved = chapterRepository.save(newChapter);
//                result.add(chapterMapper.toChapterDTO(saved));
//            }
//        }
//
//        return result;
//    }
}