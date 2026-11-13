package com.llmchad.chat.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.llmchad.chat.entity.MessageEntity;
import com.llmchad.chat.model.Message;

public interface MessageRepository extends JpaRepository<MessageEntity, UUID> {

    // WHERE conversation_id = ? ORDER BY created_at ASC, trả thẳng record Message
    List<Message> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
}
