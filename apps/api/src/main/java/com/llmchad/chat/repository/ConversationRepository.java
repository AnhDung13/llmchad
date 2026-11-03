package com.llmchad.chat.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.llmchad.chat.model.Conversation;
import com.llmchad.common.Paging;

@Repository
public class ConversationRepository {
    private final JdbcClient jdbcClient;

    public ConversationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<Conversation> findById(UUID userId, UUID convoId) {
        String sql = "SELECT id, title, created_at, modified_at from conversations WHERE id = :id AND user_id = :userId";
        return jdbcClient.sql(sql).param("userId", userId).param("id", convoId).query(ConversationRepository::mapRow)
                .optional();
    }

    public List<Conversation> getConversations(UUID userId, Paging payload) {
        long offset = (long) payload.size() * (payload.page() - 1);
        String sql = "SELECT id, title, created_at, modified_at from conversations WHERE user_id = :userId ORDER BY modified_at DESC LIMIT :size OFFSET :offset ";
        return jdbcClient.sql(sql).param("userId", userId).param("size", payload.size()).param("offset", offset)
                .query(ConversationRepository::mapRow).list();
    }

    public Conversation insertConversation(UUID userId, String title) {
        String sql = "INSERT INTO conversations (user_id, title) VALUES (:userId, :title) RETURNING id, title, created_at, modified_at";
        return jdbcClient.sql(sql).param("userId", userId)
                .param("title", title)
                .query(ConversationRepository::mapRow).single();
    }

    public Optional<Conversation> updateConversation(UUID userId, UUID convoId, String title) {
        String sql = "UPDATE conversations SET title = :title, modified_at = now() WHERE id = :id AND user_id = :userId RETURNING id, title, created_at, modified_at";
        return jdbcClient.sql(sql).param("userId", userId).param("id", convoId).param("title", title)
                .query(ConversationRepository::mapRow).optional();
    }

    public void touchConversation(UUID convoId) {
        String sql = "UPDATE conversations SET modified_at = now() WHERE id = :id";
        jdbcClient.sql(sql).param("id", convoId).update();
    }

    public boolean deleteConversation(UUID userId, UUID convoId) {
        String sql = "DELETE FROM conversations WHERE id = :id AND user_id = :userId";
        return jdbcClient.sql(sql).param("userId", userId).param("id", convoId).update() > 0;
    }

    private static Conversation mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Conversation(rs.getObject("id", UUID.class),
                rs.getString("title"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("modified_at").toInstant());
    }

}
