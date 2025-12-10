package com.learning.progress.service;

import com.learning.progress.common.UserStatus;
import com.learning.progress.dto.user.*;
import com.learning.progress.dto.DataResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserService {
    StudentProfileDTO createStudent(CreateStudentRequest request);
    StudentProfileDTO updateStudent(Long userId, UpdateStudentRequest request);
    StudentProfileDTO updateStudentStatus(Long userId, UserStatus status);
    DataResponse<List<StudentProfileDTO>> getStudentList(int page, int size, String searchText, List<String> status, List<String> roleName, String sortBy, String sortDir);
    TeacherProfileDTO createTeacher(CreateUserRequest request);
    TeacherProfileDTO updateTeacher(Long userId, UpdateUserRequest request);
    TeacherProfileDTO updateTeacherStatus(Long userId, String status);
    DataResponse<List<TeacherProfileDTO>> getTeacherList(int page, int size, String searchText, List<String> status, List<String> roleName, String sortBy, String sortDir);
    UserProfileDTO getUserProfile(Long userId, boolean isCurrentUser);

    Object updateUserProfile(Long userId, UpdateProfileDTO updateProfileDTO);

    void requestChangeEmail(Long userId, ChangeEmailRequest request);
    UserProfileDTO confirmChangeEmail(String token);

    byte[] generateTeacherImportTemplate();
    byte[] generateStudentImportTemplate();
    void importTeachersFromExcel(MultipartFile file);
    String getTeacherTemplateSasUrl();
    void importStudentsFromExcel(MultipartFile file);
    String getStudentTemplateSasUrl();

    String updateUserAvatar(Long userId, MultipartFile file);
    byte[] exportStudents(String searchText,
                          List<String> status,
                          List<String> roleName,
                          List<Long> classIds);

    byte[] exportAllTeachers(String searchText,
                             List<String> status,
                             List<String> roleName);

    List<StudentProfileDTO> bulkUpdateStudentStatus(BulkUpdateStatusRequest request);

    List<TeacherProfileDTO> bulkUpdateTeacherStatus(BulkUpdateStatusRequest request);

    byte[] downloadStudentValidationFile(MultipartFile file);
    byte[] downloadTeacherValidationFile(MultipartFile file);
}