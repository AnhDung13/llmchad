package com.llmchad.chat.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.llmchad.chat.entity.ConversationEntity;
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
        // Paging đếm trang từ 1, PageRequest đếm từ 0
        return conversationRepository.findByUserIdOrderByModifiedAtDesc(userId,
                PageRequest.of(payload.page() - 1, payload.size()));
    }

    public Conversation getConversation(UUID userId, UUID convoId) {
        return conversationRepository.findByIdAndUserId(convoId, userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, MessageKey.CONVERSATION_NOT_FOUND));
    }

    public Conversation createConversation(UUID userId, String title) {
        // saveAndFlush: INSERT chạy ngay để có created_at / modified_at do DB sinh trả về
        return conversationRepository.saveAndFlush(new ConversationEntity(userId, title)).toModel();
    }

    /**
     * Dirty checking: load entity, đổi field, Hibernate tự sinh UPDATE. Cần @Transactional để entity load ra vẫn
     * được Hibernate quản lý tới lúc flush. Không có transaction thì entity bị "detached" ngay sau findById và
     * rename không được lưu.
     */
    @Transactional
    public Conversation updateConversation(UUID userId, UUID convoId, String title) {
        ConversationEntity conversation = conversationRepository.findById(convoId)
                // Của người khác thì báo không tồn tại, giống như không tìm thấy
                .filter(c -> c.getUserId().equals(userId))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, MessageKey.CONVERSATION_NOT_FOUND));
        conversation.rename(title);
        // Bình thường UPDATE chạy lúc commit. Flush sớm để chạy ngay và đọc modified_at mới về entity trước khi trả
        conversationRepository.flush();
        return conversation.toModel();
    }

    public void deleteConversation(UUID userId, UUID convoId) {
        if (conversationRepository.deleteByIdAndUserId(convoId, userId) == 0) {
            throw new AppException(HttpStatus.NOT_FOUND, MessageKey.CONVERSATION_NOT_FOUND);
        }
    }
}
