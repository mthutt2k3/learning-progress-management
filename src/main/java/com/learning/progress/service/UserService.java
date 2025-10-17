package com.learning.progress.service;

import com.learning.progress.common.UserStatus;
import com.learning.progress.dto.*;
import com.learning.progress.dto.request.CreateStudentRequest;
import com.learning.progress.dto.request.CreateUserRequest;
import com.learning.progress.dto.response.DataResponse;
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
}