package com.learning.progress.controller;

import com.learning.progress.common.Const;
import com.learning.progress.common.UserStatus;
import com.learning.progress.dto.AccountDTO;
import com.learning.progress.dto.request.CreateNewAccountRequest;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/account")
@Tag(name = "Account", description = "Account management APIs for ADMIN")
public class AccountController {
    @Autowired
    private AccountService accountService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all accounts", description = "Retrieve a paginated list of all accounts with optional filtering by text (email or name), statuses, roles, and sorting (ADMIN only)")
    public ResponseEntity<DataResponse<List<AccountDTO>>> listAccounts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String text,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) List<String> roleName,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return new ResponseEntity<>(accountService.listAccounts(page, size, text, status, roleName, sortBy, sortDir), HttpStatus.OK);
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get account by user ID", description = "Retrieve account details for a user (ADMIN only)")
    public ResponseEntity<DataResponse<AccountDTO>> getAccountByUserId(@PathVariable Long userId) {
        AccountDTO response = accountService.getAccountByUserId(userId);
        return new ResponseEntity<>(DataResponse.success(response, "Account retrieved successfully"), HttpStatus.OK);
    }

    @PostMapping("")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new account from scratch", description = "Create a new account without an existing user (ADMIN only)")
    public ResponseEntity<DataResponse<AccountDTO>> createNewAccount(@Valid @RequestBody CreateNewAccountRequest request) {
        AccountDTO response = accountService.createNewAccount(request);
        return new ResponseEntity<>(DataResponse.success(response, Const.RESULT_MESSAGE_CODE.CREATE_SUCCESSFUL), HttpStatus.CREATED);
    }


    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update account", description = "Update account (ADMIN only)")
    public ResponseEntity<DataResponse<?>> updateAccount(@PathVariable Long id, @Valid @RequestParam String email) {
        return new ResponseEntity<>(DataResponse.success(accountService.updateAccount(id, email), Const.RESULT_MESSAGE_CODE.UPDATE_SUCCESSFUL), HttpStatus.OK);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update account status", description = "Update status of an account (ADMIN only)")
    public ResponseEntity<DataResponse<?>> updateStatusAccount(@PathVariable Long id, @Valid @RequestParam UserStatus userStatus) {
        return new ResponseEntity<>(DataResponse.success(accountService.updateStatusAccount(id, userStatus), Const.RESULT_MESSAGE_CODE.UPDATE_SUCCESSFUL), HttpStatus.OK);
    }

}
