package com.llmchad.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.llmchad.auth.oauth2.UserIdentityRepository;

/** Giống UserRepositoryTest: PostgreSQL thật, mỗi test rollback ở cuối. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ UserIdentityRepository.class, UserRepository.class })
class UserIdentityRepositoryTest {

    @Autowired
    private UserIdentityRepository identityRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcClient jdbcClient;

    /** user_identities.user_id có khoá ngoại tới users → phải có user thật trước */
    private UUID newUserId() {
        return userRepository.insertUser("user-" + UUID.randomUUID() + "@example.com", null, "An").id();
    }

    private String randomProviderUserId() {
        return UUID.randomUUID().toString();
    }

    @Test
    void insert_thenFindUserId_returnsLinkedUser() {
        UUID userId = newUserId();
        String githubId = randomProviderUserId();

        identityRepository.insert(userId, "github", githubId);

        assertThat(identityRepository.findUserId("github", githubId)).contains(userId);
    }

    @Test
    void findUserId_unknownIdentity_isEmpty() {
        assertThat(identityRepository.findUserId("google", randomProviderUserId())).isEmpty();
    }

    @Test
    void findUserId_sameIdDifferentProvider_isEmpty() {
        // Id của Google và GitHub là hai không gian riêng: cùng chuỗi id nhưng khác
        // provider là người khác
        UUID userId = newUserId();
        String id = randomProviderUserId();
        identityRepository.insert(userId, "github", id);

        assertThat(identityRepository.findUserId("google", id)).isEmpty();
    }

    @Test
    void oneUser_canLinkBothProviders() {
        UUID userId = newUserId();
        String googleId = randomProviderUserId();
        String githubId = randomProviderUserId();

        identityRepository.insert(userId, "google", googleId);
        identityRepository.insert(userId, "github", githubId);

        assertThat(identityRepository.findUserId("google", googleId)).contains(userId);
        assertThat(identityRepository.findUserId("github", githubId)).contains(userId);
    }

    @Test
    void insert_duplicateIdentity_violatesUniqueConstraint() {
        // Một tài khoản GitHub chỉ được liên kết với một user
        String githubId = randomProviderUserId();
        identityRepository.insert(newUserId(), "github", githubId);

        assertThatThrownBy(() -> identityRepository.insert(newUserId(), "github", githubId))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void insert_unknownUser_violatesForeignKey() {
        assertThatThrownBy(() -> identityRepository.insert(UUID.randomUUID(), "github", randomProviderUserId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- UserRepository: phần dùng cho OAuth ----------

    @Test
    void insertUser_withoutPassword_isAllowedForOAuthUsers() {
        String email = "user-" + UUID.randomUUID() + "@example.com";

        userRepository.insertUser(email, null, "An");

        assertThat(userRepository.findCredentialsByEmail(email).orElseThrow().passwordHash()).isNull();
    }

    @Test
    void newUser_isVerifiedByDefault() {
        // Mọi cách tạo user hiện nay đều đã xác minh email → cột có DEFAULT now()
        UUID userId = userRepository.insertUser(randomEmail(), "hash", "An").id();

        assertThat(emailVerifiedAt(userId)).isNotNull();
    }

    @Test
    void markEmailVerifiedByProvider_verifiedUser_keepsPassword() {
        // Đăng ký bằng email (đã nhập mã xác minh), sau đó đăng nhập Google: vẫn phải đăng nhập bằng mật khẩu được
        String email = randomEmail();
        UUID userId = userRepository.insertUser(email, "hash", "An").id();

        userRepository.markEmailVerifiedByProvider(userId);

        assertThat(userRepository.findCredentialsByEmail(email).orElseThrow().passwordHash()).isEqualTo("hash");
    }

    @Test
    void markEmailVerifiedByProvider_neverVerifiedUser_losesPasswordAndBecomesVerified() {
        // Tài khoản tạo trước khi có xác minh email (cột NULL): mật khẩu có thể do kẻ gian đặt
        String email = randomEmail();
        UUID userId = userRepository.insertUser(email, "hash", "An").id();
        jdbcClient.sql("UPDATE users SET email_verified_at = NULL WHERE id = :id").param("id", userId).update();

        userRepository.markEmailVerifiedByProvider(userId);

        assertThat(userRepository.findCredentialsByEmail(email).orElseThrow().passwordHash()).isNull();
        assertThat(emailVerifiedAt(userId)).isNotNull();
    }

    private String randomEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private OffsetDateTime emailVerifiedAt(UUID userId) {
        return jdbcClient.sql("SELECT email_verified_at FROM users WHERE id = :id").param("id", userId)
                .query(OffsetDateTime.class).single();
    }
}
