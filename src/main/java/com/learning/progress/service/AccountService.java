package com.learning.progress.service;

import com.learning.progress.common.UserStatus;
import com.learning.progress.dto.AccountDTO;
import com.learning.progress.dto.request.CreateAccountRequest;
import com.learning.progress.dto.request.CreateNewAccountRequest;
import com.learning.progress.dto.response.CreateAccountResponse;
import com.learning.progress.dto.response.DataResponse;
import com.learning.progress.entity.User;

import java.util.List;

public interface AccountService {
    DataResponse<List<AccountDTO>> listAccounts(int page, int size, String text, List<String> status, List<String> roleName, String sortBy, String sortDir);

    AccountDTO getAccountByUserId(Long userId);

    AccountDTO createNewAccount(CreateNewAccountRequest request);

    User createAccountForExistUser(User user, String username, String password);

    AccountDTO updateAccount(Long id, CreateNewAccountRequest updateAccountRequest);

    AccountDTO updateStatusAccount(Long id, UserStatus userStatus);
}
