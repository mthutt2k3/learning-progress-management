package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
import com.learning.progress.common.RoleName;
import com.learning.progress.common.UserStatus;
import com.learning.progress.dto.AccountDTO;
import com.learning.progress.dto.request.CreateAccountRequest;
import com.learning.progress.dto.response.CreateAccountResponse;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.entity.Role;
import com.learning.progress.entity.User;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.UserMapper;
import com.learning.progress.repository.UserRepository;
import com.learning.progress.service.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AccountServiceImpl implements AccountService {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserMapper userMapper;

    @Override
    @Transactional
    public CreateAccountResponse createAccountForUser(CreateAccountRequest createAccountRequest) {

        if (userRepository.existsByUserName(createAccountRequest.getUserName())) {
            throw new ApiException(Const.ERROR_MESSAGE.USERNAME_EXISTS, 400);
        }

        User user = userRepository.findById(createAccountRequest.getUserId())
                .orElseThrow(() -> new ApiException(Const.ERROR_MESSAGE.ACCOUNT_NOT_FOUND, 400));

        user.setUserName(createAccountRequest.getUserName());
        user.setPassword(passwordEncoder.encode(createAccountRequest.getPassword()));
        user.setMustChangePassword(true);

        userRepository.save(user);

        return userMapper.toCreateAccountResponse(user);
    }


    @Override
    public CreateAccountResponse getAccountByUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(Const.ERROR_MESSAGE.ACCOUNT_NOT_FOUND, 400));

        return userMapper.toCreateAccountResponse(user);
    }

    @Override
    public CreateAccountResponse updateAccountStatus(Long id, UserStatus status) {
        return null;
    }

    @Override
    public DataResponse<List<AccountDTO>> listAccounts(int page, int size, String text, List<UserStatus> status, List<RoleName> roleName, String sortBy, String sortDir) {
        // Validate page
        if (page < 0) {
            throw new ApiException(Const.ERROR_MESSAGE.INVALID_PAGE, 400);
        }

        // Validate size
        if (size < 1 || size > 100) {
            throw new ApiException(Const.ERROR_MESSAGE.INVALID_SIZE, 400);
        }

        // Validate status
        if (status != null && status.isEmpty()) {
            throw new ApiException(Const.ERROR_MESSAGE.INVALID_STATUS, 400);
        }

        // Validate roleName
        if (roleName != null && roleName.isEmpty()) {
            throw new ApiException(Const.ERROR_MESSAGE.INVALID_ROLE_NAME, 400);
        }

        // Validate sortBy
        String[] validSortFields = {"id", "userName", "email", "fullName", "status"};
        boolean isValidSortField = false;
        for (String field : validSortFields) {
            if (field.equalsIgnoreCase(sortBy)) {
                isValidSortField = true;
                break;
            }
        }
        if (!isValidSortField) {
            throw new ApiException(Const.ERROR_MESSAGE.INVALID_SORT_BY, 400);
        }

        // Validate sortDir
        if (!sortDir.equalsIgnoreCase("asc") && !sortDir.equalsIgnoreCase("desc")) {
            throw new ApiException(Const.ERROR_MESSAGE.INVALID_SORT_DIR, 400);
        }

        // Map fullName sort to firstName for database query
        String sortField = "fullName".equalsIgnoreCase(sortBy) ? "firstName" : sortBy;

        // Create Sort object
        Sort sort = Sort.by(sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, sortField);
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<User> userPage;

        if (text != null && !text.isBlank()) {
            if (status != null && !status.isEmpty() && roleName != null && !roleName.isEmpty()) {
                userPage = userRepository.findByTextAndStatusInAndRoleNameIn(text, status, roleName, pageable);
            } else if (status != null && !status.isEmpty()) {
                userPage = userRepository.findByTextAndStatusIn(text, status, pageable);
            } else if (roleName != null && !roleName.isEmpty()) {
                userPage = userRepository.findByTextAndRoleNameIn(text, roleName, pageable);
            } else {
                userPage = userRepository.findByText(text, pageable);
            }
        } else {
            if (status != null && !status.isEmpty() && roleName != null && !roleName.isEmpty()) {
                userPage = userRepository.findByStatusInAndRoleNameIn(status, roleName, pageable);
            } else if (status != null && !status.isEmpty()) {
                userPage = userRepository.findByStatusIn(status, pageable);
            } else if (roleName != null && !roleName.isEmpty()) {
                userPage = userRepository.findByRoleNameIn(roleName, pageable);
            } else {
                userPage = userRepository.findAll(pageable);
            }
        }

        List<AccountDTO> accounts = userPage.getContent().stream()
                .map(userMapper::toAccountDTO)
                .collect(Collectors.toList());

        return DataResponse.<List<AccountDTO>>builder()
                .traceId(org.slf4j.MDC.get("traceId"))
                .success(true)
                .message("Accounts retrieved successfully")
                .data(accounts)
                .timestamp(java.time.LocalDateTime.now())
                .page(page)
                .size(size)
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .build();
    }
}
