package com.llmchad.chat.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.llmchad.chat.model.Message;
import com.llmchad.chat.repository.MessageRepositoriy;

@Service
public class MessageService {
    private final MessageRepositoriy messageRepositoriy;
    private final ConversationService conversationService;

    public MessageService(MessageRepositoriy messageRepositoriy, ConversationService conversationService) {
        this.messageRepositoriy = messageRepositoriy;
        this.conversationService = conversationService;
    }

    public List<Message> getMessages(UUID userId, UUID convoId) {
        // Check convo if it belong to user
        conversationService.getConversation(userId, convoId);
        return messageRepositoriy.getMessages(convoId);
    }
}
