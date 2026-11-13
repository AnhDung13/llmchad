package com.llmchad.chat.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;

import com.llmchad.chat.model.Message;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Bảng messages. Tin nhắn chỉ thêm, không sửa, nên mọi cột đều updatable = false.
 *
 * role giữ String: cột có CHECK (role IN ('user', 'assistant')) viết thường, còn @Enumerated(STRING)
 * sẽ lưu tên hằng của enum (USER, ASSISTANT) và vi phạm CHECK.
 */
@Entity
@Table(name = "messages")
public class MessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(updatable = false)
    private UUID conversationId;

    @Column(updatable = false)
    private String role;

    @Column(updatable = false)
    private String content;

    @Column(updatable = false)
    private String reasoning;

    @Generated
    @Column(updatable = false)
    private Instant createdAt;

    protected MessageEntity() {
    }

    public MessageEntity(UUID conversationId, String role, String content, String reasoning) {
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.reasoning = reasoning;
    }

    public Message toModel() {
        return new Message(id, role, content, reasoning, createdAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getReasoning() {
        return reasoning;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
