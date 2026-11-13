package com.llmchad.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.llmchad.config.JpaConfig;
import com.llmchad.chat.entity.ConversationEntity;
import com.llmchad.chat.model.Conversation;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class ConversationRepositoryTest {

    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private JdbcClient jdbcClient;

    private UUID owner;
    private UUID otherUser;

    @BeforeEach
    void setUp() {
        owner = insertUser();
        otherUser = insertUser();
    }

    // Trong 1 transaction now() luôn trả cùng một giá trị, nên test thứ tự phải tự đặt modified_at.
    // Dữ liệu đã nằm trong DB (saveAndFlush), nên sửa bằng SQL được. Các query trả record (projection) đọc thẳng
    // từ DB nên thấy giá trị mới.
    private void setModifiedAt(UUID convoId, Instant time) {
        jdbcClient.sql("UPDATE conversations SET modified_at = :t WHERE id = :id")
                .param("t", Timestamp.from(time)).param("id", convoId).update();
    }

    private UUID insertUser() {
        return jdbcClient.sql("""
                INSERT INTO users (email, password_hash, display_name)
                VALUES (:email, 'hash', 'Test') RETURNING id
                """)
                .param("email", "user-" + UUID.randomUUID() + "@example.com")
                .query(UUID.class).single();
    }

    // Giống ConversationService.createConversation
    private Conversation insert(UUID userId, String title) {
        return conversationRepository.saveAndFlush(new ConversationEntity(userId, title)).toModel();
    }

    private List<Conversation> page(UUID userId, int size, int pageFromZero) {
        return conversationRepository.findByUserIdOrderByModifiedAtDesc(userId, PageRequest.of(pageFromZero, size));
    }

    @Test
    void saveAndFlush_returnsCreatedRowWithDbGeneratedFields() {
        Conversation convo = insert(owner, "Hello");

        assertThat(convo.id()).isNotNull();
        assertThat(convo.title()).isEqualTo("Hello");
        assertThat(convo.createdAt()).isNotNull();
        assertThat(convo.modifiedAt()).isNotNull();
    }

    @Test
    void findByIdAndUserId_onlyReturnsOwnersConversation() {
        Conversation convo = insert(owner, "Hello");

        assertThat(conversationRepository.findByIdAndUserId(convo.id(), owner)).contains(convo);
        assertThat(conversationRepository.findByIdAndUserId(convo.id(), otherUser)).isEmpty();
    }

    @Test
    void findByUserId_filtersByUserAndSortsByModifiedAtDesc() {
        Conversation oldest = insert(owner, "oldest");
        Conversation newest = insert(owner, "newest");
        Conversation middle = insert(owner, "middle");
        insert(otherUser, "not mine");
        setModifiedAt(oldest.id(), Instant.parse("2026-01-01T00:00:00Z"));
        setModifiedAt(middle.id(), Instant.parse("2026-01-02T00:00:00Z"));
        setModifiedAt(newest.id(), Instant.parse("2026-01-03T00:00:00Z"));

        assertThat(page(owner, 20, 0)).extracting(Conversation::title).containsExactly("newest", "middle", "oldest");
    }

    @Test
    void findByUserId_paginates() {
        for (int i = 1; i <= 5; i++) {
            Conversation c = insert(owner, "c" + i);
            setModifiedAt(c.id(), Instant.parse("2026-01-0" + i + "T00:00:00Z"));
        }

        assertThat(page(owner, 2, 0)).extracting(Conversation::title).containsExactly("c5", "c4");
        assertThat(page(owner, 2, 1)).extracting(Conversation::title).containsExactly("c3", "c2");
        assertThat(page(owner, 2, 2)).extracting(Conversation::title).containsExactly("c1");
        assertThat(page(owner, 2, 3)).isEmpty();
    }

    @Test
    void rename_thenFlush_updatesTitleAndModifiedAt() {
        // Giống ConversationService.updateConversation: đổi field của entity, flush → Hibernate tự UPDATE
        ConversationEntity entity = conversationRepository.saveAndFlush(new ConversationEntity(owner, "Hello"));
        Instant old = Instant.parse("2020-01-01T00:00:00Z");
        setModifiedAt(entity.getId(), old);

        entity.rename("Renamed");
        conversationRepository.flush();

        Conversation inDb = conversationRepository.findByIdAndUserId(entity.getId(), owner).orElseThrow();
        assertThat(inDb.title()).isEqualTo("Renamed");
        assertThat(inDb.modifiedAt()).isAfter(old);
        // modified_at mới do DB sinh đã được Hibernate đọc lại vào entity: service trả đúng giá trị này
        assertThat(entity.toModel()).isEqualTo(inDb);
    }

    @Test
    void touch_bumpsModifiedAt() {
        Conversation convo = insert(owner, "Hello");
        Instant old = Instant.parse("2020-01-01T00:00:00Z");
        setModifiedAt(convo.id(), old);

        conversationRepository.touch(convo.id());

        assertThat(conversationRepository.findByIdAndUserId(convo.id(), owner).orElseThrow().modifiedAt())
                .isAfter(old);
    }

    @Test
    void deleteByIdAndUserId_otherUsersConversation_deletesNothing() {
        Conversation convo = insert(owner, "Hello");

        assertThat(conversationRepository.deleteByIdAndUserId(convo.id(), otherUser)).isZero();
        assertThat(conversationRepository.findByIdAndUserId(convo.id(), owner)).isPresent();
    }

    @Test
    void deleteByIdAndUserId_ownConversation_deletesRow() {
        Conversation convo = insert(owner, "Hello");

        assertThat(conversationRepository.deleteByIdAndUserId(convo.id(), owner)).isEqualTo(1);
        assertThat(conversationRepository.findByIdAndUserId(convo.id(), owner)).isEmpty();
    }
}
