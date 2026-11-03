package com.llmchad.chat.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.llmchad.chat.dto.ConversationRequest;
import com.llmchad.chat.dto.MessageRequest;
import com.llmchad.chat.model.Conversation;
import com.llmchad.chat.model.Message;
import com.llmchad.chat.service.ConversationService;
import com.llmchad.chat.service.MessageService;
import com.llmchad.chat.service.StreamingService;
import com.llmchad.common.ApiResponse;
import com.llmchad.common.JwtUtils;
import com.llmchad.common.Paging;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/conversations")
public class ConversationController {
    private final StreamingService streamingService;
    private final ConversationService conversationService;
    private final MessageService messageService;

    public ConversationController(ConversationService conversationService, MessageService messageService,
            StreamingService streamingService) {
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.streamingService = streamingService;

    }

    @GetMapping
    public ApiResponse<List<Conversation>> getListConversation(@AuthenticationPrincipal Jwt jwt,
            @Valid Paging payload) {
        UUID userId = JwtUtils.subjectToUUID(jwt);
        return ApiResponse.success(HttpStatus.OK, null, conversationService.getConversations(userId, payload));
    }

    @GetMapping("/{id}")
    public ApiResponse<Conversation> getConversation(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        UUID userId = JwtUtils.subjectToUUID(jwt);
        return ApiResponse.success(HttpStatus.OK, null, conversationService.getConversation(userId, id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Conversation>> createConversation(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ConversationRequest payload) {
        UUID userId = JwtUtils.subjectToUUID(jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(HttpStatus.CREATED, null,
                conversationService.createConversation(userId, payload.title())));
    }

    @PutMapping("/{id}")
    public ApiResponse<Conversation> updateConversation(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody ConversationRequest payload) {
        UUID userId = JwtUtils.subjectToUUID(jwt);
        return ApiResponse.success(HttpStatus.OK, null,
                conversationService.updateConversation(userId, id, payload.title()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConversation(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        UUID userId = JwtUtils.subjectToUUID(jwt);
        conversationService.deleteConversation(userId, id);
        return ResponseEntity.noContent().build();
    }

    // messages
    @GetMapping("/{id}/messages")
    public ApiResponse<List<Message>> getMessages(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        UUID userId = JwtUtils.subjectToUUID(jwt);
        return ApiResponse.success(HttpStatus.OK, null, messageService.getMessages(userId, id));
    }

    @PostMapping("/{id}/stream")
    public SseEmitter stream(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id, @Valid @RequestBody MessageRequest request) {
        UUID userId = JwtUtils.subjectToUUID(jwt);
        return streamingService.stream(userId, id, request);
    }

}
