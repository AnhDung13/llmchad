package com.llmchad.chat.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.llmchad.chat.model.Conversation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Bảng conversations. Xem UserEntity về lý do tách entity khỏi record và @Generated. */
@Entity
@Table(name = "conversations")
public class ConversationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // UUID thường thay vì @ManyToOne UserEntity: chỉ dùng để lọc theo chủ sở hữu
    @Column(updatable = false)
    private UUID userId;

    private String title;

    // updatable = false: @Generated chỉ bỏ cột khỏi INSERT, còn UPDATE vẫn ghi lại giá trị cũ nếu không chặn
    @Generated
    @Column(updatable = false)
    private Instant createdAt;

    // Mỗi lần Hibernate INSERT/UPDATE dòng này (ví dụ sau rename), nó ghi modified_at = now() rồi đọc giá trị về
    @Generated(event = { EventType.INSERT, EventType.UPDATE }, sql = "now()")
    private Instant modifiedAt;

    protected ConversationEntity() {
    }

    public ConversationEntity(UUID userId, String title) {
        this.userId = userId;
        this.title = title;
    }

    /**
     * Chỉ đổi field trong Java, không gọi SQL. Entity đang được quản lý (load ra trong cùng transaction) thì
     * lúc flush/commit Hibernate thấy field đổi và tự sinh UPDATE (dirty checking).
     */
    public void rename(String title) {
        this.title = title;
    }

    public Conversation toModel() {
        return new Conversation(id, title, createdAt, modifiedAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getModifiedAt() {
        return modifiedAt;
    }
}
