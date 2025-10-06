package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
import com.learning.progress.common.UserStatus;
import com.learning.progress.dto.request.CreateAccountRequest;
import com.learning.progress.dto.response.CreateAccountResponse;
import com.learning.progress.entity.User;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.UserMapper;
import com.learning.progress.repository.UserRepository;
import com.learning.progress.service.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
