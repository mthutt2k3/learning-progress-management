package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
import com.learning.progress.common.RoleName;
import com.learning.progress.common.UserStatus;
import com.learning.progress.dto.AccountDTO;
import com.learning.progress.dto.request.CreateNewAccountRequest;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.entity.Role;
import com.learning.progress.entity.User;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.UserMapper;
import com.learning.progress.repository.RoleRepository;
import com.learning.progress.repository.UserRepository;
import com.learning.progress.service.AccountService;
import com.learning.progress.service.EmailService;
import com.learning.progress.util.AppValidator;
import com.learning.progress.util.DataUtil;
import com.learning.progress.util.JwtUtil;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AccountServiceImpl implements AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountServiceImpl.class);

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

    @Autowired
    private EmailService emailService;

    @Autowired
    private AppValidator appValidator;

    @Autowired
    private JwtUtil jwtUtil;
    /**
     * Retrieves a paginated list of accounts based on search criteria, status, role, and sorting parameters.
     *
     * @param page        Page number for pagination.
     * @param size        Number of items per page.
     * @param text        Search text for filtering users.
     * @param statusStr   List of user status strings to filter.
     * @param roleNameStr List of role name strings to filter.
     * @param sortBy      Field to sort by.
     * @param sortDir     Sorting direction (asc or desc).
     * @return DataResponse containing the list of accounts and pagination details.
     */
    @Override
    public DataResponse<List<AccountDTO>> listAccounts(int page, int size, String text, List<String> statusStr, List<String> roleNameStr, String sortBy, String sortDir) {
        String traceId = MDC.get("traceId");
        log.info("[{}] Listing accounts with page: {}, size: {}, text: {}, statuses: {}, roles: {}, sortBy: {}, sortDir: {}",
                traceId, page, size, text, statusStr, roleNameStr, sortBy, sortDir);

        // Validate pagination and sort parameters
        appValidator.validatePaginationParams(page, size);
        appValidator.validateSortParams(List.of("createdAt", "userName", "email", "firstName", "lastName", "status"), sortBy, sortDir);
        log.debug("[{}] Pagination and sort parameters validated", traceId);

        // Convert status and role strings to enums
        List<UserStatus> statuses = appValidator.validateAndConvertEnums(statusStr, UserStatus.class);
        List<RoleName> roleNames = appValidator.validateAndConvertEnums(roleNameStr, RoleName.class);
        log.debug("[{}] Converted statuses: {}, roles: {}", traceId, statuses, roleNames);

        // Create Sort and Pageable objects
        Sort sort = Sort.by(sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        // Fetch users based on filters
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
        log.debug("[{}] Retrieved {} users for page {}", traceId, userPage.getTotalElements(), page);

        // Map users to DTOs
        List<AccountDTO> accounts = userPage.getContent().stream()
                .map(userMapper::toAccountDTO)
                .collect(Collectors.toList());

        log.info("[{}] Successfully retrieved account list with {} accounts", traceId, accounts.size());
        return DataResponse.<List<AccountDTO>>builder()
                .traceId(traceId)
                .success(true)
                .message(Const.RESULT_MESSAGE_CODE.RETRIEVE_SUCCESSFUL)
                .data(accounts)
                .timestamp(java.time.LocalDateTime.now())
                .page(page)
                .size(size)
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .build();
    }

    /**
     * Retrieves an account by user ID.
     *
     * @param userId The ID of the user to retrieve.
     * @return AccountDTO containing the user details.
     */
    @Override
    public AccountDTO getAccountByUserId(Long userId) {
        String traceId = MDC.get("traceId");
        log.info("[{}] Retrieving account for userId: {}", traceId, userId);

        // Fetch user by ID
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("[{}] Account not found for userId: {}", traceId, userId);
                    return new ApiException(Const.ACCOUNT.ACCOUNT_NOT_FOUND, HttpStatus.BAD_REQUEST.value());
                });

        log.info("[{}] Successfully retrieved account for userId: {}", traceId, userId);
        return userMapper.toAccountDTO(user);
    }

    /**
     * Creates a new account with a generated username and password, and sends a notification email.
     *
     * @param request The request containing account details and role.
     * @return AccountDTO containing the created account details.
     */
    @Override
    @Transactional
    public AccountDTO createNewAccount(CreateNewAccountRequest request) {
        String traceId = MDC.get("traceId");
        log.info("[{}] Creating new account with role: {}", traceId, request.getRoleName());

        // Fetch role
        Role role = roleRepository.findByName(RoleName.valueOf(request.getRoleName().toUpperCase()))
                .orElseThrow(() -> {
                    log.error("[{}] Role not found: {}", traceId, request.getRoleName());
                    return new ApiException(Const.ROLE.NOT_FOUND, HttpStatus.BAD_REQUEST.value());
                });

        // Map request to user entity
        User newUser = userMapper.toUser(request);
        newUser.setMustChangePassword(true);
        newUser.setRole(role);
        newUser.setStatus(UserStatus.PENDING);
        newUser.setMustUpdateProfile(true);

        // Save user and flush
        userRepository.saveAndFlush(newUser);
        entityManager.clear();
        userRepository.save(newUser);
        log.debug("[{}] User saved with ID: {}", traceId, newUser.getId());

        // Generate username and password
        String username = DataUtil.generateUsername(request.getRoleName().toString(), newUser.getId());
        String password = DataUtil.generateRandomPassword(8);
        this.createAccountForExistUser(newUser, username, password);
        log.debug("[{}] Generated username: {}, password for userId: {}", traceId, username, newUser.getId());
        // Map to DTO
        AccountDTO accountDTO = userMapper.toAccountDTO(newUser);
        accountDTO.setUserName(newUser.getUserName());

        log.info("[{}] Successfully created account for userId: {}", traceId, newUser.getId());
        return accountDTO;
    }

    /**
     * Creates an account for an existing user with a specified username and password.
     *
     * @param user     The existing user entity.
     * @param username The username to set.
     * @param password The password to set.
     * @return Updated User entity.
     */
    @Override
    @Transactional
    public User createAccountForExistUser(User user, String username, String password) {
        String traceId = MDC.get("traceId");
        log.info("[{}] Creating account for existing user with username: {}", traceId, username);

        // Check for username uniqueness
        if (userRepository.existsByUserName(username)) {
            log.error("[{}] Username already exists: {}", traceId, username);
            throw new ApiException(Const.USER.USERNAME_EXISTS, HttpStatus.BAD_REQUEST.value());
        }

        // Update user details
        user.setUserName(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setMustChangePassword(true);

        userRepository.save(user);
        log.info("[{}] Account created for existing user with username: {}", traceId, username);

        emailService.sendNewAccountEmail(user, username, password);

        return user;
    }


    @Override
    @Transactional
    public AccountDTO updateAccount(Long id, @Valid String email) {
        String traceId = MDC.get("traceId");

        if(!DataUtil.isValidEmail(email)){
            throw new ApiException(Const.EMAIL.INVALID, HttpStatus.BAD_REQUEST.value());
        }

        // Fetch user by ID
        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("[{}] User not found for userId: {}", traceId, id);
                    return new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });
        if (!UserStatus.PENDING.equals(user.getStatus())) {
            throw new ApiException(Const.ACCOUNT.FORBIDDEN_EMAIL_CHANGE_ACTIVE_USER, HttpStatus.FORBIDDEN.value());
        }

        String password = DataUtil.generateRandomPassword(8);

        // Update user fields
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        // Save updates
        userRepository.save(user);
        log.info("[{}] Successfully updated account for userId: {}", traceId, id);

        emailService.sendNewAccountEmail(user, user.getUserName(), password);

        return userMapper.toAccountDTO(user);
    }

    @Override
    public AccountDTO updateStatusAccount(Long id, UserStatus newStatus) {
        String traceId = MDC.get("traceId");
        log.info("[{}] Updating status for userId: {} to {}", traceId, id, newStatus);

        User targetUser = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("[{}] User not found for userId: {}", traceId, id);
                    return new ApiException(Const.USER.USER_NOT_FOUND, HttpStatus.BAD_REQUEST.value());
                });

        Long userIdFromCurrentRequest = jwtUtil.extractUserIdFromCurrentRequest();

        // Lấy thông tin admin đang thao tác
        User currentUser = userRepository.findById(userIdFromCurrentRequest)
                .orElseThrow(() -> new ApiException("Current user not found", HttpStatus.UNAUTHORIZED.value()));

        UserStatus oldStatus = targetUser.getStatus();

        // Quy tắc trạng thái
        if (UserStatus.PENDING.equals(oldStatus)) {
            if (!UserStatus.ACTIVE.equals(newStatus)) {
                throw new ApiException(Const.ACCOUNT.CANNOT_CHANGE_STATUS_TO_ACTIVE_MANUALLY, HttpStatus.FORBIDDEN.value());
            }
        } else { // ACTIVE hoặc INACTIVE
            if (UserStatus.PENDING.equals(newStatus)) {
                throw new ApiException(Const.ACCOUNT.CANNOT_CHANGE_STATUS_TO_PENDING, HttpStatus.FORBIDDEN.value());
            }
        }

        // Quy tắc Admin
        if (RoleName.ADMIN.equals(targetUser.getRole().getName())) {
            if (RoleName.ADMIN.equals(currentUser.getRole().getName())) {
                throw new ApiException("Admin cannot change status of other Admins or themselves", HttpStatus.FORBIDDEN.value());
            }
        }

        // Update status
        targetUser.setStatus(newStatus);
        userRepository.save(targetUser);
        log.info("[{}] Successfully updated status for userId: {} to {}", traceId, id, newStatus);

        return userMapper.toAccountDTO(targetUser);
    }

}