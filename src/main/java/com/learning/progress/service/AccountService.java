package com.learning.progress.service;

import com.learning.progress.common.UserStatus;
import com.learning.progress.dto.request.CreateAccountRequest;
import com.learning.progress.dto.response.CreateAccountResponse;

public interface AccountService {
    CreateAccountResponse createAccountForUser(CreateAccountRequest createAccountRequest);

    CreateAccountResponse getAccountByUserId(Long userId);

    CreateAccountResponse updateAccountStatus(Long id, UserStatus status);
}
