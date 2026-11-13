package com.llmchad.chat.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.llmchad.chat.model.Message;
import com.llmchad.chat.repository.MessageRepository;

@Service
public class MessageService {
    private final MessageRepository messageRepository;
    private final ConversationService conversationService;

    public MessageService(MessageRepository messageRepository, ConversationService conversationService) {
        this.messageRepository = messageRepository;
        this.conversationService = conversationService;
    }

    public List<Message> getMessages(UUID userId, UUID convoId) {
        // Check convo if it belong to user
        conversationService.getConversation(userId, convoId);
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(convoId);
    }
}
