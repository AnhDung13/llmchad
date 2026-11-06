package com.llmchad.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;

import com.llmchad.auth.model.User;
import com.llmchad.auth.model.UserCredentials;

/**
 * @JdbcTest chỉ dựng DataSource + JdbcClient + Flyway, mỗi test chạy trong transaction và rollback ở cuối.
 * replace = NONE: dùng đúng PostgreSQL trong application.properties (docker compose up -d db),
 * không thay bằng DB nhúng.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(UserRepository.class)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    private String randomEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    void existEmail_beforeAndAfterInsert() {
        String email = randomEmail();
        assertThat(userRepository.existEmail(email)).isFalse();

        userRepository.insertUser(email, "hash", "An");

        assertThat(userRepository.existEmail(email)).isTrue();
    }

    @Test
    void insertUser_returnsCreatedUser() {
        String email = randomEmail();

        User created = userRepository.insertUser(email, "hash", "An");

        assertThat(created.id()).isNotNull();
        assertThat(created.email()).isEqualTo(email);
        assertThat(created.displayName()).isEqualTo("An");
        assertThat(created.createdAt()).isNotNull();
    }

    @Test
    void findCredentialsByEmail_returnsUserAndHash() {
        String email = randomEmail();
        userRepository.insertUser(email, "hash", "An");

        UserCredentials creds = userRepository.findCredentialsByEmail(email).orElseThrow();

        assertThat(creds.passwordHash()).isEqualTo("hash");
        assertThat(creds.user().email()).isEqualTo(email);
        assertThat(creds.user().displayName()).isEqualTo("An");
        assertThat(creds.user().id()).isNotNull();
        assertThat(creds.user().createdAt()).isNotNull();
    }

    @Test
    void findCredentialsByEmail_unknown_isEmpty() {
        assertThat(userRepository.findCredentialsByEmail(randomEmail())).isEmpty();
    }

    @Test
    void findById_existingAndMissing() {
        String email = randomEmail();
        userRepository.insertUser(email, "hash", "An");
        UUID id = userRepository.findCredentialsByEmail(email).orElseThrow().user().id();

        assertThat(userRepository.findById(id)).get().extracting("email").isEqualTo(email);
        assertThat(userRepository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void insertUser_duplicateEmail_violatesUniqueConstraint() {
        String email = randomEmail();
        userRepository.insertUser(email, "hash", "An");

        assertThatThrownBy(() -> userRepository.insertUser(email, "hash2", "Binh"))
                .isInstanceOf(DuplicateKeyException.class);
    }
}
