package com.learning.progress.service;

import com.learning.progress.common.RoleName;
import com.learning.progress.common.UserStatus;
import com.learning.progress.dto.AccountDTO;
import com.learning.progress.dto.request.CreateAccountRequest;
import com.learning.progress.dto.response.CreateAccountResponse;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.entity.Role;

import java.util.List;

public interface AccountService {
    CreateAccountResponse createAccountForUser(CreateAccountRequest createAccountRequest);

    CreateAccountResponse getAccountByUserId(Long userId);

    CreateAccountResponse updateAccountStatus(Long id, UserStatus status);

    DataResponse<List<AccountDTO>> listAccounts(int page, int size, String text, List<UserStatus> status, List<RoleName> roleName, String sortBy, String sortDir);
}
