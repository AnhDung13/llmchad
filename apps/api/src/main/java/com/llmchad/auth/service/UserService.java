package com.llmchad.auth.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.llmchad.auth.model.User;
import com.llmchad.auth.repository.UserRepository;
import com.llmchad.common.MessageKey;
import com.llmchad.exception.AppException;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, MessageKey.UNAUTHORIZED));
    }

}
