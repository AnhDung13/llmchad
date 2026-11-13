package com.llmchad.chat.service;

import static com.llmchad.support.TestAssertions.assertAppException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import com.llmchad.chat.entity.ConversationEntity;
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
    void getConversations_convertsOneBasedPageToPageRequest() {
        when(conversationRepository.findByUserIdOrderByModifiedAtDesc(userId, PageRequest.of(1, 20)))
                .thenReturn(List.of(convo));

        // Paging trang 2 (đếm từ 1) = PageRequest trang 1 (đếm từ 0)
        assertThat(conversationService.getConversations(userId, new Paging(20, 2))).containsExactly(convo);
    }

    @Test
    void getConversation_found_returnsIt() {
        when(conversationRepository.findByIdAndUserId(convoId, userId)).thenReturn(Optional.of(convo));

        assertThat(conversationService.getConversation(userId, convoId)).isEqualTo(convo);
    }

    @Test
    void getConversation_missingOrNotOwned_throwsNotFound() {
        when(conversationRepository.findByIdAndUserId(convoId, userId)).thenReturn(Optional.empty());

        assertAppException(() -> conversationService.getConversation(userId, convoId),
                HttpStatus.NOT_FOUND, MessageKey.CONVERSATION_NOT_FOUND);
    }

    @Test
    void createConversation_savesEntityForUser() {
        // Trả lại đúng entity được truyền vào (repository thật còn gán id, timestamp)
        when(conversationRepository.saveAndFlush(any(ConversationEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(conversationService.createConversation(userId, "Hello").title()).isEqualTo("Hello");

        ArgumentCaptor<ConversationEntity> saved = ArgumentCaptor.forClass(ConversationEntity.class);
        verify(conversationRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(userId);
    }

    @Test
    void updateConversation_owned_renamesAndFlushes() {
        ConversationEntity entity = new ConversationEntity(userId, "Old");
        when(conversationRepository.findById(convoId)).thenReturn(Optional.of(entity));

        assertThat(conversationService.updateConversation(userId, convoId, "New").title()).isEqualTo("New");
        assertThat(entity.getTitle()).isEqualTo("New");
        verify(conversationRepository).flush();
    }

    @Test
    void updateConversation_otherUsers_throwsNotFoundAndKeepsTitle() {
        ConversationEntity entity = new ConversationEntity(UUID.randomUUID(), "Old");
        when(conversationRepository.findById(convoId)).thenReturn(Optional.of(entity));

        assertAppException(() -> conversationService.updateConversation(userId, convoId, "Hacked"),
                HttpStatus.NOT_FOUND, MessageKey.CONVERSATION_NOT_FOUND);
        assertThat(entity.getTitle()).isEqualTo("Old");
    }

    @Test
    void updateConversation_missing_throwsNotFound() {
        when(conversationRepository.findById(convoId)).thenReturn(Optional.empty());

        assertAppException(() -> conversationService.updateConversation(userId, convoId, "New"),
                HttpStatus.NOT_FOUND, MessageKey.CONVERSATION_NOT_FOUND);
    }

    @Test
    void deleteConversation_deleted_doesNotThrow() {
        when(conversationRepository.deleteByIdAndUserId(convoId, userId)).thenReturn(1);

        assertThatCode(() -> conversationService.deleteConversation(userId, convoId)).doesNotThrowAnyException();
    }

    @Test
    void deleteConversation_nothingDeleted_throwsNotFound() {
        when(conversationRepository.deleteByIdAndUserId(convoId, userId)).thenReturn(0);

        assertAppException(() -> conversationService.deleteConversation(userId, convoId),
                HttpStatus.NOT_FOUND, MessageKey.CONVERSATION_NOT_FOUND);
    }
}
