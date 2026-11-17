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
        String sql = "SELECT id, role, content, reasoning, sources, created_at FROM messages WHERE conversation_id = :convoId ORDER BY created_at ASC";
        return jdbcClientl.sql(sql).param("convoId", convoId).query(MessageRepositoriy::mapRow).list();
    }

    public Message insertMessage(UUID convoId, String role, String content, String reasoning) {
        return insertMessage(convoId, role, content, reasoning, null);
    }

    /** `sourcesJson`: mảng JSON [{"title","url"}] hoặc null */
    public Message insertMessage(UUID convoId, String role, String content, String reasoning, String sourcesJson) {
        String sql = "INSERT INTO messages (conversation_id, role, content, reasoning, sources) VALUES (:convoId, :role, :content, :reasoning, CAST(:sources AS jsonb)) RETURNING id, conversation_id, role, content, reasoning, sources, created_at";
        return jdbcClientl.sql(sql).param("convoId", convoId).param("role", role).param("content", content)
                .param("reasoning", reasoning).param("sources", sourcesJson)
                .query(MessageRepositoriy::mapRow).single();
    }

    private static Message mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Message(rs.getObject("id", UUID.class), rs.getString("role"), rs.getString("content"),
                rs.getString("reasoning"), rs.getString("sources"), rs.getTimestamp("created_at").toInstant());
    }
}
