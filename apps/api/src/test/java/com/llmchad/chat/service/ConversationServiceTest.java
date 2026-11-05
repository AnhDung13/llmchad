package com.llmchad.chat.service;

import static com.llmchad.support.TestAssertions.assertAppException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.llmchad.chat.model.Conversation;
import com.llmchad.chat.repository.ConversationRepository;
import com.llmchad.common.MessageKey;
import com.llmchad.common.Paging;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @InjectMocks
    private ConversationService conversationService;

    private final UUID userId = UUID.randomUUID();
    private final UUID convoId = UUID.randomUUID();
    private final Conversation convo = new Conversation(convoId, "Hello", Instant.now(), Instant.now());

    @Test
    void getConversations_delegatesToRepository() {
        Paging paging = new Paging(20, 1);
        when(conversationRepository.getConversations(userId, paging)).thenReturn(List.of(convo));

        assertThat(conversationService.getConversations(userId, paging)).containsExactly(convo);
    }

    @Test
    void getConversation_found_returnsIt() {
        when(conversationRepository.findById(userId, convoId)).thenReturn(Optional.of(convo));

        assertThat(conversationService.getConversation(userId, convoId)).isEqualTo(convo);
    }

    @Test
    void getConversation_missingOrNotOwned_throwsNotFound() {
        when(conversationRepository.findById(userId, convoId)).thenReturn(Optional.empty());

        assertAppException(() -> conversationService.getConversation(userId, convoId),
                HttpStatus.NOT_FOUND, MessageKey.CONVERSATION_NOT_FOUND);
    }

    @Test
    void createConversation_returnsInsertedRow() {
        when(conversationRepository.insertConversation(userId, "Hello")).thenReturn(convo);

        assertThat(conversationService.createConversation(userId, "Hello")).isEqualTo(convo);
    }

    @Test
    void updateConversation_found_returnsUpdatedRow() {
        when(conversationRepository.updateConversation(userId, convoId, "Hello")).thenReturn(Optional.of(convo));

        assertThat(conversationService.updateConversation(userId, convoId, "Hello")).isEqualTo(convo);
    }

    @Test
    void updateConversation_missingOrNotOwned_throwsNotFound() {
        when(conversationRepository.updateConversation(userId, convoId, "New")).thenReturn(Optional.empty());

        assertAppException(() -> conversationService.updateConversation(userId, convoId, "New"),
                HttpStatus.NOT_FOUND, MessageKey.CONVERSATION_NOT_FOUND);
    }

    @Test
    void deleteConversation_deleted_doesNotThrow() {
        when(conversationRepository.deleteConversation(userId, convoId)).thenReturn(true);

        assertThatCode(() -> conversationService.deleteConversation(userId, convoId)).doesNotThrowAnyException();
    }

    @Test
    void deleteConversation_nothingDeleted_throwsNotFound() {
        when(conversationRepository.deleteConversation(userId, convoId)).thenReturn(false);

        assertAppException(() -> conversationService.deleteConversation(userId, convoId),
                HttpStatus.NOT_FOUND, MessageKey.CONVERSATION_NOT_FOUND);
    }
}
