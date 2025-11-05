package com.learning.progress.service;

import com.learning.progress.common.UserStatus;
import com.learning.progress.dto.account.AccountDTO;
import com.learning.progress.dto.account.CreateNewAccountRequest;
import com.learning.progress.dto.DataResponse;
import com.learning.progress.entity.User;

import java.util.List;

public interface AccountService {
    DataResponse<List<AccountDTO>> listAccounts(int page, int size, String text, List<String> status, List<String> roleName, String sortBy, String sortDir);

    AccountDTO getAccountByUserId(Long userId);

    AccountDTO createNewAccount(CreateNewAccountRequest request);

    User createAccountForExistUser(User user, String username, String password);

    AccountDTO updateAccount(Long id, String email);

    AccountDTO updateStatusAccount(Long id, UserStatus userStatus);

    void deleteAccount(Long id);
}
