package com.llmchad.auth.service;

import static com.llmchad.support.TestAssertions.assertAppException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.llmchad.auth.model.User;
import com.llmchad.auth.repository.UserRepository;
import com.llmchad.common.MessageKey;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void getUserById_found_returnsUser() {
        User user = new User(UUID.randomUUID(), "an@example.com", "An", Instant.now());
        when(userRepository.findUserById(user.id())).thenReturn(Optional.of(user));

        assertThat(userService.getUserById(user.id())).isEqualTo(user);
    }

    @Test
    void getUserById_missing_throwsUnauthorized() {
        UUID id = UUID.randomUUID();
        when(userRepository.findUserById(id)).thenReturn(Optional.empty());

        assertAppException(() -> userService.getUserById(id), HttpStatus.UNAUTHORIZED, MessageKey.UNAUTHORIZED);
    }
}
