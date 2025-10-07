package com.learning.progress.controller;

import com.learning.progress.common.Const;
import com.learning.progress.common.UserStatus;
import com.learning.progress.dto.AccountDTO;
import com.learning.progress.dto.request.CreateAccountRequest;
import com.learning.progress.dto.response.CreateAccountResponse;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Accounts retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid filter or sort parameters"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Only ADMIN can access")
    })
    public ResponseEntity<DataResponse<List<AccountDTO>>> listAccounts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String text,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) List<String> roleName,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return new ResponseEntity<>(accountService.listAccounts(page, size, text, status, roleName, sortBy, sortDir), HttpStatus.OK);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new account", description = "Create a new account for a user (ADMIN only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Account created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Only ADMIN can create accounts"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<DataResponse<CreateAccountResponse>> createAccountForExistUser(@Valid @RequestBody CreateAccountRequest request) {
        CreateAccountResponse response = accountService.createAccountForUser(request);
        return new ResponseEntity<>(DataResponse.success(response, Const.CRUD_MESSAGE_CODE.CREATE_SUCCESSFUL), HttpStatus.CREATED);
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get account by user ID", description = "Retrieve account details for a user (ADMIN only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Only ADMIN can access"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public ResponseEntity<DataResponse<CreateAccountResponse>> getAccountByUserId(@PathVariable Long userId) {
        CreateAccountResponse response = accountService.getAccountByUserId(userId);
        return new ResponseEntity<>(DataResponse.success(response, "Account retrieved successfully"), HttpStatus.OK);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update account status", description = "Update status of an account (ADMIN only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account status updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid status"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Only ADMIN can update accounts"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public ResponseEntity<DataResponse<CreateAccountResponse>> updateAccountStatus(@PathVariable Long id, @Valid @RequestParam UserStatus status) {
        CreateAccountResponse response = accountService.updateAccountStatus(id, status);
        return new ResponseEntity<>(DataResponse.success(response, Const.CRUD_MESSAGE_CODE.UPDATE_SUCCESSFUL), HttpStatus.OK);
    }


}
