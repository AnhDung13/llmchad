package com.llmchad.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.llmchad.chat.model.Message;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MessageRepositoriy.class)
class MessageRepositoriyTest {

    @Autowired
    private MessageRepositoriy messageRepositoriy;
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

    // Trong 1 transaction now() không đổi, nên test thứ tự phải tự đặt created_at
    private void setCreatedAt(UUID messageId, Instant time) {
        jdbcClient.sql("UPDATE messages SET created_at = :t WHERE id = :id")
                .param("t", Timestamp.from(time)).param("id", messageId).update();
    }

    @Test
    void insertMessage_userMessageWithoutReasoning() {
        Message msg = messageRepositoriy.insertMessage(convoId, "user", "Hi", null);

        assertThat(msg.id()).isNotNull();
        assertThat(msg.role()).isEqualTo("user");
        assertThat(msg.content()).isEqualTo("Hi");
        assertThat(msg.reasoning()).isNull();
        assertThat(msg.createdAt()).isNotNull();
    }

    @Test
    void insertMessage_assistantMessageKeepsReasoning() {
        Message msg = messageRepositoriy.insertMessage(convoId, "assistant", "Answer", "Thinking...");

        assertThat(msg.reasoning()).isEqualTo("Thinking...");
    }

    @Test
    void insertMessage_invalidRole_isRejectedByCheckConstraint() {
        assertThatThrownBy(() -> messageRepositoriy.insertMessage(convoId, "system", "x", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void getMessages_onlyThisConversation_oldestFirst() {
        Message second = messageRepositoriy.insertMessage(convoId, "assistant", "second", null);
        Message first = messageRepositoriy.insertMessage(convoId, "user", "first", null);
        setCreatedAt(first.id(), Instant.parse("2026-01-01T00:00:00Z"));
        setCreatedAt(second.id(), Instant.parse("2026-01-01T00:00:01Z"));
        messageRepositoriy.insertMessage(insertConversation(), "user", "other convo", null);

        assertThat(messageRepositoriy.getMessages(convoId))
                .extracting(Message::content).containsExactly("first", "second");
    }

    @Test
    void getMessages_emptyConversation_returnsEmptyList() {
        assertThat(messageRepositoriy.getMessages(convoId)).isEmpty();
    }
}
