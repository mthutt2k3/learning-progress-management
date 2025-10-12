package com.learning.progress.service;

import com.learning.progress.dto.AccountDTO;
import com.learning.progress.dto.request.CreateStudentRequest;
import com.learning.progress.dto.response.StudentProfileResponse;
import com.learning.progress.dto.request.CreateUserRequest;
import com.learning.progress.dto.response.CreateUserResponse;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.dto.response.UserProfileResponse;

import java.util.List;

public interface UserService {
    UserProfileResponse getCurrentUserProfile();

    CreateUserResponse createUser(CreateUserRequest request);

    DataResponse<List<StudentProfileResponse>> getStudentList(int page, int size, String text, List<String> status, List<String> roleName, String sortBy, String sortDir);

    DataResponse<List<UserProfileResponse>> getTeacherList(int page, int size, String text, List<String> status, List<String> roleName, String sortBy, String sortDir);

    AccountDTO createStudent(CreateStudentRequest request);

    AccountDTO updateStudent(Long userId, CreateStudentRequest request);

    void updateStudentStatus(Long userId, String status);
}
