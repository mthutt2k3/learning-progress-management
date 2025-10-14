package com.learning.progress.service;

import com.learning.progress.dto.StudentProfileDTO;
import com.learning.progress.dto.TeacherProfileDTO;
import com.learning.progress.dto.UpdateUserProfileDTO;
import com.learning.progress.dto.UserProfileDTO;
import com.learning.progress.dto.request.CreateStudentRequest;
import com.learning.progress.dto.request.CreateUserRequest;
import com.learning.progress.dto.response.DataResponse;

import java.util.List;

public interface UserService {
    StudentProfileDTO createStudent(CreateStudentRequest request);
    StudentProfileDTO updateStudent(Long userId, CreateStudentRequest request);
    StudentProfileDTO updateStudentStatus(Long userId, String status);
    DataResponse<List<StudentProfileDTO>> getStudentList(int page, int size, String searchText, List<String> status, List<String> roleName, String sortBy, String sortDir);
    TeacherProfileDTO createTeacher(CreateUserRequest request);
    TeacherProfileDTO updateTeacher(Long userId, CreateUserRequest request);
    TeacherProfileDTO updateTeacherStatus(Long userId, String status);
    DataResponse<List<TeacherProfileDTO>> getTeacherList(int page, int size, String searchText, List<String> status, List<String> roleName, String sortBy, String sortDir);
    UserProfileDTO getUserProfile(Long userId, boolean isCurrentUser);

    Object updateUserProfile(Long userId, UpdateUserProfileDTO updateUserProfileDTO);
}