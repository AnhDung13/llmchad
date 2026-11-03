package com.llmchad.chat.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.llmchad.chat.model.Conversation;
import com.llmchad.chat.repository.ConversationRepository;
import com.llmchad.common.MessageKey;
import com.llmchad.common.Paging;
import com.llmchad.exception.AppException;

@Service
public class ConversationService {
    private final ConversationRepository conversationRepository;

    public ConversationService(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;

    }

    public List<Conversation> getConversations(UUID userId, Paging payload) {
        return conversationRepository.getConversations(userId, payload);
    }

    public Conversation getConversation(UUID userId, UUID convoId) {
        return conversationRepository.findById(userId, convoId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, MessageKey.CONVERSATION_NOT_FOUND));
    }

    public Conversation createConversation(UUID userId, String title) {
        return conversationRepository.insertConversation(userId, title);
    }

    public Conversation updateConversation(UUID userId, UUID convoId, String title) {
        return conversationRepository.updateConversation(userId, convoId, title)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, MessageKey.CONVERSATION_NOT_FOUND));
    }

    public void deleteConversation(UUID userId, UUID convoId) {
        boolean deleted = conversationRepository.deleteConversation(userId, convoId);
        if (!deleted) {
            throw new AppException(HttpStatus.NOT_FOUND, MessageKey.CONVERSATION_NOT_FOUND);
        }
    }
}
