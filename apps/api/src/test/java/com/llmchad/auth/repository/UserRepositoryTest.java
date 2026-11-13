package com.llmchad.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;

import com.llmchad.config.JpaConfig;
import com.llmchad.auth.entity.UserEntity;
import com.llmchad.auth.model.User;
import com.llmchad.auth.model.UserCredentials;

/**
 * @DataJpaTest chỉ dựng DataSource + JPA (Hibernate) + các Spring Data repository (+ JdbcClient), mỗi test chạy
 * trong transaction và rollback ở cuối. Không cần @Import: repository là interface, Spring Data tự tạo bean.
 * replace = NONE: dùng đúng PostgreSQL trong application.properties (docker compose up -d db),
 * không thay bằng DB nhúng.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    private String randomEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    // Giống cách AuthService / OAuthLoginService tạo user
    private User insertUser(String email, String passwordHash, String displayName) {
        return userRepository.saveAndFlush(new UserEntity(email, passwordHash, displayName)).toModel();
    }

    @Test
    void existsByEmail_beforeAndAfterInsert() {
        String email = randomEmail();
        assertThat(userRepository.existsByEmail(email)).isFalse();

        insertUser(email, "hash", "An");

        assertThat(userRepository.existsByEmail(email)).isTrue();
    }

    @Test
    void saveAndFlush_returnsCreatedUserWithDbGeneratedFields() {
        String email = randomEmail();

        User created = insertUser(email, "hash", "An");

        assertThat(created.id()).isNotNull();
        assertThat(created.email()).isEqualTo(email);
        assertThat(created.displayName()).isEqualTo("An");
        // created_at do DB sinh (current_timestamp), Hibernate đọc lại sau INSERT
        assertThat(created.createdAt()).isNotNull();
    }

    @Test
    void findCredentialsByEmail_returnsUserAndHash() {
        String email = randomEmail();
        insertUser(email, "hash", "An");

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
    void findUserById_existingAndMissing() {
        String email = randomEmail();
        User created = insertUser(email, "hash", "An");

        assertThat(userRepository.findUserById(created.id())).contains(created);
        assertThat(userRepository.findUserById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void saveAndFlush_duplicateEmail_violatesUniqueConstraint() {
        String email = randomEmail();
        insertUser(email, "hash", "An");

        // Spring đổi lỗi của Hibernate thành DuplicateKeyException: AuthService bắt đúng loại này
        assertThatThrownBy(() -> insertUser(email, "hash2", "Binh"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void updatePassword_setsNewHash_andReturnsZeroForUnknownUser() {
        String email = randomEmail();
        User created = insertUser(email, "old-hash", "An");

        assertThat(userRepository.updatePassword(created.id(), "new-hash")).isEqualTo(1);
        // clearAutomatically: entity cũ trong bộ nhớ đã bị xoá nên đọc lại thấy hash mới từ DB
        assertThat(userRepository.findCredentialsByEmail(email).orElseThrow().passwordHash()).isEqualTo("new-hash");
        assertThat(userRepository.updatePassword(UUID.randomUUID(), "new-hash")).isZero();
    }
}
