package com.learning.progress.service.impl;

import com.learning.progress.dto.clazz.ClassChapterDTO;
import com.learning.progress.dto.clazz.SyncClassChapterRequest;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.entity.ClassChapter;
import com.learning.progress.entity.Clazz;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.ClassChapterMapper;
import com.learning.progress.repository.ClassChapterRepository;
import com.learning.progress.repository.ClassRepository;
import com.learning.progress.service.ClassChapterService;
import com.learning.progress.util.JwtUtil;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
    private ClassChapterMapper classChapterMapper;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private Validator validator;

    @Transactional
    public List<ClassChapterDTO> syncClassChapters(Long classId, List<SyncClassChapterRequest> request) {
        // Validate class
        Clazz classEntity = classRepository.findById(classId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Clazz không tồn tại", HttpStatus.NOT_FOUND.value()));

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

        // Validate non-deleted
        for (SyncClassChapterRequest req : nonDeletedRequests) {
            Set<ConstraintViolation<SyncClassChapterRequest>> violations = validator.validate(req, SyncClassChapterRequest.NotDeleted.class);
            if (!violations.isEmpty()) {
                throw new ApiException(violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(", ")), HttpStatus.BAD_REQUEST.value());
            }
        }

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

        String currentUser = jwtUtil.extractUsernameFromCurrentRequest();
        OffsetDateTime now = OffsetDateTime.now();
        List<ClassChapterDTO> result = new ArrayList<>();

        // Process DELETE
        for (SyncClassChapterRequest deleteReq : deleteRequests) {
            ClassChapter classChapter = classChapterRepository.findById(deleteReq.getId())
                    .filter(c -> c.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException("Clazz chapter không tồn tại", HttpStatus.NOT_FOUND.value()));
            classChapter.setDeletedBy(currentUser);
            classChapter.setDeletedAt(now);
            classChapterRepository.save(classChapter);
        }

        // Process UPDATE
        List<SyncClassChapterRequest> updateRequests = nonDeletedRequests.stream()
                .filter(req -> req.getId() != null)
                .collect(Collectors.toList());
        for (SyncClassChapterRequest req : updateRequests) {
            ClassChapter classChapter = classChapterRepository.findById(req.getId())
                    .filter(c -> c.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException("Clazz chapter không tồn tại", HttpStatus.NOT_FOUND.value()));
            classChapter.setClassChapterName(req.getClassChapterName());
            classChapter.setClassChapterContent(req.getClassChapterContent());
            classChapter.setOrderNumber(req.getOrderNumber());
            classChapter.setUpdatedBy(currentUser);
            classChapter.setUpdatedAt(now);
            result.add(classChapterMapper.toClassChapterDTO(classChapterRepository.save(classChapter)));
        }

        // Process CREATE
        List<SyncClassChapterRequest> newRequests = nonDeletedRequests.stream()
                .filter(req -> req.getId() == null)
                .collect(Collectors.toList());
        for (SyncClassChapterRequest req : newRequests) {
            ClassChapter newChapter = new ClassChapter();
            newChapter.setClazz(classEntity);
            newChapter.setClassChapterName(req.getClassChapterName());
            newChapter.setClassChapterContent(req.getClassChapterContent());
            newChapter.setOrderNumber(req.getOrderNumber());
            newChapter.setCreatedBy(currentUser);
            newChapter.setUpdatedBy(currentUser);
            newChapter.setCreatedAt(now);
            newChapter.setUpdatedAt(now);
            result.add(classChapterMapper.toClassChapterDTO(classChapterRepository.save(newChapter)));
        }

        return result;
    }

    public ClassChapterDTO getClassChapter(Long id) {
        ClassChapter classChapter = classChapterRepository.findById(id)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Clazz chapter không tồn tại", HttpStatus.NOT_FOUND.value()));
        return classChapterMapper.toClassChapterDTO(classChapter);
    }

    public DataResponse<List<ClassChapterDTO>> getClassChapterList(Long classId, int page, int size, String searchText) {
        classRepository.findById(classId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException("Clazz không tồn tại", HttpStatus.NOT_FOUND.value()));

        Page<ClassChapter> chapterPage = classChapterRepository.findByClassIdAndSearchText(classId, searchText, PageRequest.of(page, size, Sort.by("orderNumber").ascending()));
        List<ClassChapterDTO> responses = chapterPage.getContent().stream()
                .map(classChapterMapper::toClassChapterDTO)
                .collect(Collectors.toList());

        return DataResponse.<List<ClassChapterDTO>>builder()
                .success(true)
                .message("Thành công")
                .data(responses)
                .page(page)
                .size(size)
                .totalElements(chapterPage.getTotalElements())
                .totalPages(chapterPage.getTotalPages())
                .build();
    }

    // Import/Export Excel
    public void exportClassChaptersToExcel(Long classId, OutputStream outputStream) {
        // Logic export class chapters sang Excel
    }

    public void importClassChaptersFromExcel(Long classId, InputStream inputStream) {
        // Logic import class chapters từ Excel
    }

    public void downloadImportTemplate(OutputStream outputStream) {
        // Tạo template Excel
    }
}