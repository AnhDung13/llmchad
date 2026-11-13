package com.llmchad.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.llmchad.config.JpaConfig;
import com.llmchad.chat.entity.MessageEntity;
import com.llmchad.chat.model.Message;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class MessageRepositoryTest {

    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private JdbcClient jdbcClient;

    private UUID convoId;

    @BeforeEach
    void setUp() {
        convoId = insertConversation();
    }

    private UUID insertConversation() {
        UUID userId = jdbcClient.sql("""
                INSERT INTO users (email, password_hash, display_name)
                VALUES (:email, 'hash', 'Test') RETURNING id
                """)
                .param("email", "user-" + UUID.randomUUID() + "@example.com")
                .query(UUID.class).single();
        return jdbcClient.sql("INSERT INTO conversations (user_id, title) VALUES (:userId, 'Test') RETURNING id")
                .param("userId", userId)
                .query(UUID.class).single();
    }

    // Giống StreamingService
    private Message insert(UUID conversationId, String role, String content, String reasoning) {
        return messageRepository.saveAndFlush(new MessageEntity(conversationId, role, content, reasoning)).toModel();
    }

    // Trong 1 transaction now() không đổi, nên test thứ tự phải tự đặt created_at
    private void setCreatedAt(UUID messageId, Instant time) {
        jdbcClient.sql("UPDATE messages SET created_at = :t WHERE id = :id")
                .param("t", Timestamp.from(time)).param("id", messageId).update();
    }

    @Test
    void insert_userMessageWithoutReasoning() {
        Message msg = insert(convoId, "user", "Hi", null);

        assertThat(msg.id()).isNotNull();
        assertThat(msg.role()).isEqualTo("user");
        assertThat(msg.content()).isEqualTo("Hi");
        assertThat(msg.reasoning()).isNull();
        assertThat(msg.createdAt()).isNotNull();
    }

    @Test
    void insert_assistantMessageKeepsReasoning() {
        Message msg = insert(convoId, "assistant", "Answer", "Thinking...");

        assertThat(msg.reasoning()).isEqualTo("Thinking...");
    }

    @Test
    void insert_invalidRole_isRejectedByCheckConstraint() {
        assertThatThrownBy(() -> insert(convoId, "system", "x", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByConversationId_onlyThisConversation_oldestFirst() {
        Message second = insert(convoId, "assistant", "second", null);
        Message first = insert(convoId, "user", "first", null);
        setCreatedAt(first.id(), Instant.parse("2026-01-01T00:00:00Z"));
        setCreatedAt(second.id(), Instant.parse("2026-01-01T00:00:01Z"));
        insert(insertConversation(), "user", "other convo", null);

        assertThat(messageRepository.findByConversationIdOrderByCreatedAtAsc(convoId))
                .extracting(Message::content).containsExactly("first", "second");
    }

    @Test
    void findByConversationId_emptyConversation_returnsEmptyList() {
        assertThat(messageRepository.findByConversationIdOrderByCreatedAtAsc(convoId)).isEmpty();
    }
}
