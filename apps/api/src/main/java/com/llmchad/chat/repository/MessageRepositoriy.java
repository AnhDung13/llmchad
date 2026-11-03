package com.llmchad.chat.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.llmchad.chat.model.Message;

@Repository
public class MessageRepositoriy {
    private final JdbcClient jdbcClientl;

    public MessageRepositoriy(JdbcClient jdbcClient) {
        this.jdbcClientl = jdbcClient;
    }

    public List<Message> getMessages(UUID convoId) {
        String sql = "SELECT id, role, content, reasoning, created_at FROM messages WHERE conversation_id = :convoId ORDER BY created_at ASC";
        return jdbcClientl.sql(sql).param("convoId", convoId).query(MessageRepositoriy::mapRow).list();
    }

    public Message insertMessage(UUID convoId, String role, String content, String reasoning) {
        String sql = "INSERT INTO messages (conversation_id, role, content, reasoning) VALUES (:convoId, :role, :content, :reasoning) RETURNING id, conversation_id, role, content, reasoning, created_at";
        return jdbcClientl.sql(sql).param("convoId", convoId).param("role", role).param("content", content)
                .param("reasoning", reasoning)
                .query(MessageRepositoriy::mapRow).single();
    }

    private static Message mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Message(rs.getObject("id", UUID.class), rs.getString("role"), rs.getString("content"),
                rs.getString("reasoning"), rs.getTimestamp("created_at").toInstant());
    }
}
