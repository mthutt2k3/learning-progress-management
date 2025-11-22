package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
import com.learning.progress.common.RoleName;
import com.learning.progress.common.UserStatus;
import com.learning.progress.dto.account.AccountDTO;
import com.learning.progress.dto.account.CreateNewAccountRequest;
import com.learning.progress.dto.DataResponse;
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
import com.learning.progress.util.TraceUtil;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
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
        final String method = "listAccounts";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} page={} size={} text={} statuses={} roles={} sortBy={} sortDir={}",
                method, traceId, page, size, text, statusStr, roleNameStr, sortBy, sortDir);

        // Validate pagination and sort parameters
        appValidator.validatePaginationParams(page, size);
        appValidator.validateSortParams(List.of("userName", "email"), sortBy, sortDir);
        log.debug("[{}] traceId={} pagination and sort params validated", method, traceId);

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
            if (statusStr != null && !statusStr.isEmpty() && roleNameStr != null && !roleNameStr.isEmpty()) {
                userPage = userRepository.findByTextAndStatusInAndRoleNameIn(text, appValidator.validateAndConvertEnums(statusStr, UserStatus.class), appValidator.validateAndConvertEnums(roleNameStr, RoleName.class), pageable);
            } else if (statusStr != null && !statusStr.isEmpty()) {
                userPage = userRepository.findByTextAndStatusIn(text, appValidator.validateAndConvertEnums(statusStr, UserStatus.class), pageable);
            } else if (roleNameStr != null && !roleNameStr.isEmpty()) {
                userPage = userRepository.findByTextAndRoleNameIn(text, appValidator.validateAndConvertEnums(roleNameStr, RoleName.class), pageable);
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
                userPage = userRepository.findAllByDeletedAtIsNull(pageable);
            }
        }
        log.debug("[{}] traceId={} retrieved {} users for page {}", method, traceId, userPage.getTotalElements(), page);

        // Map users to DTOs
        List<AccountDTO> accounts = userPage.getContent().stream()
                .map(userMapper::toAccountDTO)
                .collect(Collectors.toList());

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} accountsCount={} durationMs={}", method, traceId, accounts.size(), durationMs);

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
        final String method = "getAccountByUserId";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} userId={}", method, traceId, userId);

        // Fetch user by ID
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> {
                    log.error("[{}] traceId={} account not found userId={}", method, traceId, userId);
                    return new ApiException(Const.ACCOUNT.ACCOUNT_NOT_FOUND, HttpStatus.BAD_REQUEST.value());
                });

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} userId={} durationMs={}", method, traceId, userId, durationMs);
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
        final String method = "createNewAccount";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} role={}", method, traceId, request.getRoleName());

        // Fetch role
        Role role = roleRepository.findByName(RoleName.valueOf(request.getRoleName().toUpperCase()))
                .orElseThrow(() -> {
                    log.error("[{}] traceId={} role not found: {}", method, traceId, request.getRoleName());
                    return new ApiException(Const.ROLE.NOT_FOUND, HttpStatus.BAD_REQUEST.value());
                });

        // Map request to user entity
        User newUser = userMapper.toUser(request);
        newUser.setMustChangePassword(true);
        newUser.setRole(role);
        newUser.setStatus(UserStatus.PENDING);

        // Save user and flush
        userRepository.saveAndFlush(newUser);
        entityManager.clear();
        userRepository.save(newUser);
        log.debug("[{}] traceId={} user saved id={}", method, traceId, newUser.getId());

        // Generate username and password
        String username = DataUtil.generateUsername(request.getRoleName().toString(), newUser.getId());
        String password = DataUtil.generateRandomPassword(8);
        this.createAccountForExistUser(newUser, username, password);
        log.debug("[{}] traceId={} generated username={} for userId={}", method, traceId, username, newUser.getId());
        // Map to DTO
        AccountDTO accountDTO = userMapper.toAccountDTO(newUser);
        accountDTO.setUserName(newUser.getUserName());

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} createdUserId={} durationMs={}", method, traceId, newUser.getId(), durationMs);
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
        final String method = "createAccountForExistUser";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} username={}", method, traceId, username);

        // Check for username uniqueness
        if (userRepository.existsByUserName(username)) {
            log.error("[{}] traceId={} username exists: {}", method, traceId, username);
            throw new ApiException(Const.USER.USERNAME_EXISTS, HttpStatus.BAD_REQUEST.value());
        }

        // Update user details
        user.setUserName(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setStatus(UserStatus.PENDING);
        user.setMustChangePassword(true);

        userRepository.save(user);
        log.info("[{}] traceId={} account created username={}", method, traceId, username);

        emailService.sendNewAccountEmail(user, username, password);

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.debug("[{}] exit traceId={} username={} durationMs={}", method, traceId, username, durationMs);
        return user;
    }


    @Override
    @Transactional
    public AccountDTO updateAccount(Long id, @Valid String email) {
        final String method = "updateAccount";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} userId={} email={}", method, traceId, id, email);

        if (!DataUtil.isValidEmail(email)) {
            log.error("[{}] traceId={} invalid email: {}", method, traceId, email);
            throw new ApiException(Const.EMAIL.INVALID, HttpStatus.BAD_REQUEST.value());
        }

        // Fetch user by ID
        User user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.error("[{}] traceId={} user not found id={}", method, traceId, id);
                    return new ApiException(Const.USER.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });
        if (!UserStatus.PENDING.equals(user.getStatus())) {
            log.error("[{}] traceId={} forbidden email change for active user id={}", method, traceId, id);
            throw new ApiException(Const.ACCOUNT.FORBIDDEN_EMAIL_CHANGE_ACTIVE_USER, HttpStatus.FORBIDDEN.value());
        }

        String password = DataUtil.generateRandomPassword(8);
        // Update user fields
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        // Save updates
        userRepository.save(user);
        log.info("[{}] traceId={} updated account id={}", method, traceId, id);

        emailService.sendNewAccountEmail(user, user.getUserName(), password);

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.debug("[{}] exit traceId={} userId={} durationMs={}", method, traceId, id, durationMs);
        return userMapper.toAccountDTO(user);
    }

    @Override
    @Transactional
    public AccountDTO updateStatusAccount(Long id, UserStatus newStatus) {
        final String method = "updateStatusAccount";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} userId={} newStatus={}", method, traceId, id, newStatus);

        User targetUser = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.error("[{}] traceId={} user not found id={}", method, traceId, id);
                    return new ApiException(Const.USER.NOT_FOUND, HttpStatus.BAD_REQUEST.value());
                });

        Long userIdFromCurrentRequest = jwtUtil.extractUserIdFromCurrentRequest();

        User currentUser = userRepository.findByIdAndDeletedAtIsNull(userIdFromCurrentRequest)
                .orElseThrow(() -> new ApiException("Current user not found", HttpStatus.UNAUTHORIZED.value()));

        UserStatus oldStatus = targetUser.getStatus();

        if (UserStatus.PENDING.equals(oldStatus)) {
            log.error("[{}] traceId={} cannot change status from PENDING userId={}", method, traceId, id);
            throw new ApiException(Const.ACCOUNT.CANNOT_CHANGE_STATUS_TO_ACTIVE_MANUALLY, HttpStatus.FORBIDDEN.value());
        }
        if (UserStatus.PENDING.equals(newStatus)) {
            log.error("[{}] traceId={} cannot change status to PENDING userId={}", method, traceId, id);
            throw new ApiException(Const.ACCOUNT.CANNOT_CHANGE_STATUS_TO_PENDING, HttpStatus.FORBIDDEN.value());
        }

        if (RoleName.ADMIN.equals(targetUser.getRole().getName())) {
            if (RoleName.ADMIN.equals(currentUser.getRole().getName())) {
                log.error("[{}] traceId={} admin cannot change admin status userId={}", method, traceId, id);
                throw new ApiException(Const.USER.ADMIN_CANNOT_CHANGE_STATUS, HttpStatus.FORBIDDEN.value());
            }
        }

        // Update status
        targetUser.setStatus(newStatus);
        userRepository.save(targetUser);
        log.info("[{}] traceId={} updated status for userId={} to {}", method, traceId, id, newStatus);

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.debug("[{}] exit traceId={} userId={} durationMs={}", method, traceId, id, durationMs);
        return userMapper.toAccountDTO(targetUser);
    }

    @Override
    public void deleteAccount(Long id) {
        final String method = "deleteAccount";
        long startNs = System.nanoTime();
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] enter traceId={} userId={}", method, traceId, id);

        User targetUser = userRepository.findByIdAndDeletedAtIsNull(id)
                .filter(user -> user.getDeletedAt() == null)
                .orElseThrow(() -> {
                    log.error("[{}] traceId={} user not found id={}", method, traceId, id);
                    return new ApiException(Const.USER.NOT_FOUND, HttpStatus.BAD_REQUEST.value());
                });

        if (!targetUser.getStatus().equals(UserStatus.PENDING)) {
            log.error("[{}] traceId={} cannot delete non-pending user id={}", method, traceId, id);
            throw new ApiException(Const.USER.PENDING_STATUS, HttpStatus.BAD_REQUEST.value());
        }

        Long userIdFromCurrentRequest = jwtUtil.extractUserIdFromCurrentRequest();

        User currentUser = userRepository.findByIdAndDeletedAtIsNull(userIdFromCurrentRequest)
                .orElseThrow(() -> new ApiException(Const.USER.NOT_FOUND, HttpStatus.UNAUTHORIZED.value()));

        if (RoleName.ADMIN.equals(targetUser.getRole().getName()) && (RoleName.ADMIN.equals(currentUser.getRole().getName()))) {
            log.error("[{}] traceId={} admin cannot delete admin userId={}", method, traceId, id);
            throw new ApiException(Const.USER.ADMIN_CANNOT_CHANGE_STATUS, HttpStatus.FORBIDDEN.value());
        }
        if (RoleName.MANAGER.equals(targetUser.getRole().getName()) && (RoleName.MANAGER.equals(currentUser.getRole().getName()))) {
            log.error("[{}] traceId={} manager cannot delete manager userId={}", method, traceId, id);
            throw new ApiException(Const.USER.MANAGER_CANNOT_CHANGE_STATUS, HttpStatus.FORBIDDEN.value());
        }

        targetUser.setDeletedBy(jwtUtil.extractEmailPrefixFromCurrentRequest());
        targetUser.setDeletedAt(OffsetDateTime.now());
        userRepository.save(targetUser);

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[{}] exit traceId={} deleted userId={} durationMs={}", method, traceId, id, durationMs);
    }

    // ...existing private helpers and other methods...
}
