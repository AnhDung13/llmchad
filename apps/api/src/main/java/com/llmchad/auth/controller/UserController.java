package com.llmchad.auth.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.llmchad.auth.model.User;
import com.llmchad.auth.service.UserService;
import com.llmchad.common.ApiResponse;
import com.llmchad.common.JwtUtils;

@RestController
@RequestMapping("/me")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping()
    public ApiResponse<User> getInfo(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtUtils.subjectToUUID(jwt);
        return ApiResponse.success(HttpStatus.OK, null, userService.getUserById(userId));
    }

}
