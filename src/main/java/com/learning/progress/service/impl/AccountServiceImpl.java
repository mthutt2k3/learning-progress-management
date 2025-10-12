package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
import com.learning.progress.common.RoleName;
import com.learning.progress.common.UserStatus;
import com.learning.progress.dto.AccountDTO;
import com.learning.progress.dto.request.CreateAccountRequest;
import com.learning.progress.dto.request.NewAccountRequest;
import com.learning.progress.dto.response.CreateAccountResponse;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.entity.Role;
import com.learning.progress.entity.User;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.UserMapper;
import com.learning.progress.repository.RoleRepository;
import com.learning.progress.repository.UserRepository;
import com.learning.progress.service.AccountService;
import com.learning.progress.util.DataUtil;
import com.learning.progress.util.EnumUtil;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
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
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private EntityManager entityManager;

    @Override
    public DataResponse<List<AccountDTO>> listAccounts(int page, int size, String text, List<String> statusStr, List<String> roleNameStr, String sortBy, String sortDir) {
        // Validate page
        if (page < 0) {
            throw new ApiException(Const.ERROR_MESSAGE.INVALID_PAGE, HttpStatus.BAD_REQUEST.value());
        }

        // Validate size
        if (size < 1 || size > 100) {
            throw new ApiException(Const.ERROR_MESSAGE.INVALID_SIZE, HttpStatus.BAD_REQUEST.value());
        }

        // Validate statuses
        List<UserStatus> statuses = statusStr != null
                ? statusStr.stream().map(s -> {
            try {
                return UserStatus.valueOf(s);
            } catch (IllegalArgumentException e) {
                throw new ApiException("Invalid UserStatus: " + s, HttpStatus.BAD_REQUEST.value());
            }
        }).collect(Collectors.toList())
                : null;

        // Validate roleNames
        List<RoleName> roleNames = roleNameStr != null
                ? roleNameStr.stream().map(r -> {
            try {
                return RoleName.valueOf(r);
            } catch (IllegalArgumentException e) {
                throw new ApiException("Invalid RoleName: " + r, HttpStatus.BAD_REQUEST.value());
            }
        }).collect(Collectors.toList())
                : null;

        // Validate sortBy
        String[] validSortFields = {"createdAt", "userName", "email", "fullName", "statuses"};
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
            if (statuses != null && !statuses.isEmpty() && roleNames != null && !roleNames.isEmpty()) {
                userPage = userRepository.findByTextAndStatusInAndRoleNameIn(text, statuses, roleNames, pageable);
            } else if (statuses != null && !statuses.isEmpty()) {
                userPage = userRepository.findByTextAndStatusIn(text, statuses, pageable);
            } else if (roleNames != null && !roleNames.isEmpty()) {
                userPage = userRepository.findByTextAndRoleNameIn(text, roleNames, pageable);
            } else {
                userPage = userRepository.findByText(text, pageable);
            }
        } else {
            if (statuses != null && !statuses.isEmpty() && roleNames != null && !roleNames.isEmpty()) {
                userPage = userRepository.findByStatusInAndRoleNameIn(statuses, roleNames, pageable);
            } else if (statuses != null && !statuses.isEmpty()) {
                userPage = userRepository.findByStatusIn(statuses, pageable);
            } else if (roleNames != null && !roleNames.isEmpty()) {
                userPage = userRepository.findByRoleNameIn(roleNames, pageable);
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

    @Override
    public AccountDTO getAccountByUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(Const.ERROR_MESSAGE.ACCOUNT_NOT_FOUND, HttpStatus.BAD_REQUEST.value()));

        return userMapper.toAccountDTO(user);
    }

    @Override
    @Transactional
    public AccountDTO createNewAccount(NewAccountRequest request) {
        // Validate formats email
        if (!request.getEmail().matches(Const.VALIDATE_INPUT.regexEmail)) {
            throw new ApiException(Const.USER.EMAIL_INVALID, 400);
        }
        //validate role name
        if(!EnumUtil.isValidEnum(RoleName.class, request.getRoleName()))
            throw new ApiException(Const.ERROR_MESSAGE.INVALID_ROLE_NAME, HttpStatus.BAD_REQUEST.value());

        // Tìm role
        Role role = roleRepository.findByName(RoleName.valueOf(request.getRoleName().toUpperCase()))
                .orElseThrow(() -> new ApiException("Invalid role: " + request.getRoleName(), 400));

        User newUser = userMapper.toUser(request);
        newUser.setMustChangePassword(true);
        newUser.setRole(role);
        newUser.setStatus(UserStatus.ACTIVE);

        // Lưu user và flush
        userRepository.saveAndFlush(newUser);
        entityManager.clear();

        userRepository.save(newUser);

        //Auto generate username and password
        String username = DataUtil.generateUsername(request.getRoleName().toString(), newUser.getId());
        String password = DataUtil.generateRandomPassword(8);
        CreateAccountResponse createAccountResponse = createAccountForExistUser(CreateAccountRequest
                .builder()
                .userId(newUser.getId())
                .userName(username)
                .password(password)
                .build()
        );

        AccountDTO accountDTO = userMapper.toAccountDTO(newUser);
        accountDTO.setUserName(createAccountResponse.getUserName());

        return accountDTO;
    }

    @Override
    @Transactional
    public CreateAccountResponse createAccountForExistUser(CreateAccountRequest createAccountRequest) {

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
    public AccountDTO updateAccount(Long id, RoleName roleName) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ApiException(Const.ERROR_MESSAGE.ACCOUNT_NOT_FOUND, HttpStatus.BAD_REQUEST.value()));

        // Tìm role
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new ApiException("Invalid role: " + roleName.name(), 400));

        user.setRole(role);

        userRepository.save(user);

        return userMapper.toAccountDTO(user);
    }

    @Override
    public AccountDTO updateStatusAccount(Long id, UserStatus userStatus) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ApiException(Const.ERROR_MESSAGE.ACCOUNT_NOT_FOUND, HttpStatus.BAD_REQUEST.value()));

        user.setStatus(userStatus);
        userRepository.save(user);

        return userMapper.toAccountDTO(user);
    }

}
