package com.llmchad.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.llmchad.chat.model.Conversation;
import com.llmchad.chat.model.Message;
import com.llmchad.chat.repository.MessageRepositoriy;
import com.llmchad.common.MessageKey;
import com.llmchad.exception.AppException;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepositoriy messageRepositoriy;
    @Mock
    private ConversationService conversationService;

    @InjectMocks
    private MessageService messageService;

    private final UUID userId = UUID.randomUUID();
    private final UUID convoId = UUID.randomUUID();

    @Test
    void getMessages_ownedConversation_returnsMessages() {
        Message msg = new Message(UUID.randomUUID(), "user", "Hi", null, Instant.now());
        when(conversationService.getConversation(userId, convoId))
                .thenReturn(new Conversation(convoId, "Hello", Instant.now(), Instant.now()));
        when(messageRepositoriy.getMessages(convoId)).thenReturn(List.of(msg));

        assertThat(messageService.getMessages(userId, convoId)).containsExactly(msg);
    }

    @Test
    void getMessages_conversationNotOwned_doesNotQueryMessages() {
        when(conversationService.getConversation(userId, convoId))
                .thenThrow(new AppException(HttpStatus.NOT_FOUND, MessageKey.CONVERSATION_NOT_FOUND));

        assertThatThrownBy(() -> messageService.getMessages(userId, convoId)).isInstanceOf(AppException.class);
        verify(messageRepositoriy, never()).getMessages(any());
    }
}
