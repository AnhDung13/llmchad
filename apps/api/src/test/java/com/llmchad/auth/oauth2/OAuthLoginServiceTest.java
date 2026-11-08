package com.llmchad.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.llmchad.auth.model.User;
import com.llmchad.auth.model.UserCredentials;
import com.llmchad.auth.repository.UserRepository;
import com.llmchad.common.HashUtils;

class OAuthLoginServiceTest {

    private UserRepository userRepository;
    private UserIdentityRepository identityRepository;
    private StringRedisTemplate redis;
    private OAuthLoginService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        identityRepository = mock(UserIdentityRepository.class);
        redis = mock(StringRedisTemplate.class);
        service = new OAuthLoginService(userRepository, identityRepository, redis);
    }

    private User user(Instant createdAt) {
        return new User(UUID.randomUUID(), "an@example.com", "An", createdAt);
    }

    @Test
    void alreadyLinked_returnsLinkedUserWithoutTouchingEmail() {
        User linked = user(Instant.now());
        when(identityRepository.findUserId("github", "42")).thenReturn(Optional.of(linked.id()));
        when(userRepository.findById(linked.id())).thenReturn(Optional.of(linked));

        assertThat(service.findOrCreateUser("github", "42", "an@example.com", "An")).isEqualTo(linked);
        verify(userRepository, never()).findCredentialsByEmail(anyString());
        verify(identityRepository, never()).insert(any(), anyString(), anyString());
    }

    @Test
    void existingUserWithSameEmail_isMarkedVerifiedAndLinked() {
        // Xoá mật khẩu hay không do DB quyết (chỉ khi user chưa từng xác minh), xem UserIdentityRepositoryTest
        User existing = user(Instant.now());
        when(identityRepository.findUserId("google", "sub-1")).thenReturn(Optional.empty());
        when(userRepository.findCredentialsByEmail("an@example.com"))
                .thenReturn(Optional.of(new UserCredentials(existing, "hashed")));

        User result = service.findOrCreateUser("google", "sub-1", "  An@Example.com ", "An");

        assertThat(result).isEqualTo(existing);
        verify(userRepository).markEmailVerifiedByProvider(existing.id());
        verify(identityRepository).insert(existing.id(), "google", "sub-1");
    }

    @Test
    void newPerson_createsUserWithoutPasswordAndLinks() {
        User created = user(Instant.now());
        when(identityRepository.findUserId("github", "42")).thenReturn(Optional.empty());
        when(userRepository.findCredentialsByEmail("an@example.com")).thenReturn(Optional.empty());
        when(userRepository.insertUser("an@example.com", null, "An")).thenReturn(created);

        assertThat(service.findOrCreateUser("github", "42", "An@example.com", "An")).isEqualTo(created);
        verify(identityRepository).insert(created.id(), "github", "42");
    }

    @Test
    void newPerson_blankName_usesEmailLocalPart() {
        when(identityRepository.findUserId("github", "42")).thenReturn(Optional.empty());
        when(userRepository.findCredentialsByEmail("an@example.com")).thenReturn(Optional.empty());
        when(userRepository.insertUser(eq("an@example.com"), eq(null), anyString())).thenReturn(user(Instant.now()));

        service.findOrCreateUser("github", "42", "an@example.com", "  ");

        verify(userRepository).insertUser("an@example.com", null, "an");
    }

    @Test
    void newPerson_longName_isCutTo256CharactersWithoutSplittingEmoji() {
        // 255 chữ + emoji (2 đơn vị UTF-16) + chữ thừa: cắt ở 256 ký tự phải giữ nguyên emoji
        String name = "a".repeat(255) + "😎" + "bcd";
        when(identityRepository.findUserId("github", "42")).thenReturn(Optional.empty());
        when(userRepository.findCredentialsByEmail("an@example.com")).thenReturn(Optional.empty());
        when(userRepository.insertUser(eq("an@example.com"), eq(null), anyString())).thenReturn(user(Instant.now()));

        service.findOrCreateUser("github", "42", "an@example.com", name);

        verify(userRepository).insertUser("an@example.com", null, "a".repeat(255) + "😎");
    }

    @Test
    void missingEmail_isRejected() {
        assertThatThrownBy(() -> service.findOrCreateUser("github", "42", null, "An"))
                .isInstanceOf(NullPointerException.class);
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> stubValueOps() {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        return valueOps;
    }

    @Test
    void createLoginCode_storesHashOfCodeAndNonceForOneMinute() {
        ValueOperations<String, String> valueOps = stubValueOps();
        UUID userId = UUID.randomUUID();

        OAuthLoginService.LoginCode loginCode = service.createLoginCode(userId);

        assertThat(loginCode.code()).matches("[0-9A-Za-z]{32}");
        assertThat(loginCode.nonce()).matches("[0-9A-Za-z]{32}").isNotEqualTo(loginCode.code());
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(key.capture(), eq(userId.toString()), eq(Duration.ofMinutes(1)));
        // Chỉ lưu hash của cả hai nửa, không lưu mã gốc
        assertThat(key.getValue())
                .isEqualTo("oauth_code:" + HashUtils.sha256(loginCode.code() + ":" + loginCode.nonce()))
                .doesNotContain(loginCode.code())
                .doesNotContain(loginCode.nonce());
    }

    @Test
    void consumeLoginCode_codeAndCookieNonceMatch_returnsUserId() {
        ValueOperations<String, String> valueOps = stubValueOps();
        UUID userId = UUID.randomUUID();
        when(valueOps.getAndDelete("oauth_code:" + HashUtils.sha256("code:nonce"))).thenReturn(userId.toString());

        assertThat(service.consumeLoginCode("code", "nonce")).contains(userId);
    }

    @Test
    void consumeLoginCode_otherBrowsersNonce_isEmpty() {
        // Login CSRF: kẻ gian gửi link có mã của hắn cho nạn nhân, trình duyệt nạn nhân không có cookie đúng
        ValueOperations<String, String> valueOps = stubValueOps();
        when(valueOps.getAndDelete(anyString())).thenReturn(null);

        assertThat(service.consumeLoginCode("code", "victims-nonce")).isEmpty();
    }

    @Test
    void consumeLoginCode_missingCookie_isEmptyWithoutTouchingRedis() {
        assertThat(service.consumeLoginCode("code", null)).isEmpty();
        verify(redis, never()).opsForValue();
    }
}
