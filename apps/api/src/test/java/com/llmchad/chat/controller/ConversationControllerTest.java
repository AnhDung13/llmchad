package com.llmchad.chat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.llmchad.chat.model.Conversation;
import com.llmchad.chat.model.Message;
import com.llmchad.chat.service.ConversationService;
import com.llmchad.chat.service.MessageService;
import com.llmchad.chat.service.StreamingService;
import com.llmchad.common.MessageKey;
import com.llmchad.common.Paging;
import com.llmchad.config.SecurityConfig;
import com.llmchad.exception.AppException;

@WebMvcTest(controllers = ConversationController.class, properties = "app.jwt.secret=dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQh")
@Import(SecurityConfig.class)
class ConversationControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private ConversationService conversationService;
        @MockitoBean
        private MessageService messageService;
        @MockitoBean
        private StreamingService streamingService;

        private final UUID userId = UUID.randomUUID();
        private final UUID convoId = UUID.randomUUID();
        private final Conversation convo = new Conversation(convoId, "Hello",
                        Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-02T00:00:00Z"));

        /**
         * Giả lập request đã đăng nhập: JWT có sub = userId, không cần ký token thật.
         */
        private JwtRequestPostProcessor asUser() {
                return jwt().jwt(j -> j.subject(userId.toString()));
        }

        // ---------- bảo mật ----------

        @Test
        void withoutToken_returns401() throws Exception {
                mockMvc.perform(get("/api/conversations"))
                                .andExpect(status().isUnauthorized());
                verifyNoInteractions(conversationService);
        }

        // ---------- list ----------

        @Test
        void list_noParams_usesDefaultPaging() throws Exception {
                when(conversationService.getConversations(userId, new Paging(20, 1))).thenReturn(List.of(convo));

                mockMvc.perform(get("/api/conversations").with(asUser()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data[0].id").value(convoId.toString()))
                                .andExpect(jsonPath("$.data[0].title").value("Hello"));
        }

        @Test
        void list_customPaging_isPassedToService() throws Exception {
                when(conversationService.getConversations(userId, new Paging(5, 2))).thenReturn(List.of());

                mockMvc.perform(get("/api/conversations").param("size", "5").param("page", "2").with(asUser()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        void list_pageZero_returns400WithKey() throws Exception {
                mockMvc.perform(get("/api/conversations").param("page", "0").with(asUser()))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(MessageKey.PAGE_INVALID));
                verifyNoInteractions(conversationService);
        }

        @Test
        void list_sizeTooLarge_returns400WithKey() throws Exception {
                mockMvc.perform(get("/api/conversations").param("size", "1000").with(asUser()))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(MessageKey.PAGE_SIZE_INVALID));
        }

        @Test
        void list_sizeZero_returns400WithKey() throws Exception {
                mockMvc.perform(get("/api/conversations").param("size", "0").with(asUser()))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(MessageKey.PAGE_SIZE_INVALID));
        }

        @Test
        void list_pageNotNumber_returns400WithKey() throws Exception {
                mockMvc.perform(get("/api/conversations").param("page", "abc").with(asUser()))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(MessageKey.TYPE_MISSMATCH));
                verifyNoInteractions(conversationService);
        }

        // ---------- get / create / update / delete ----------

        @Test
        void get_found_returns200() throws Exception {
                when(conversationService.getConversation(userId, convoId)).thenReturn(convo);

                mockMvc.perform(get("/api/conversations/{id}", convoId).with(asUser()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.title").value("Hello"));
        }

        @Test
        void get_notFound_returns404WithKey() throws Exception {
                when(conversationService.getConversation(userId, convoId))
                                .thenThrow(new AppException(HttpStatus.NOT_FOUND, MessageKey.CONVERSATION_NOT_FOUND));

                mockMvc.perform(get("/api/conversations/{id}", convoId).with(asUser()))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.message").value(MessageKey.CONVERSATION_NOT_FOUND));
        }

        @Test
        void create_valid_returns201() throws Exception {
                when(conversationService.createConversation(userId, "Hello")).thenReturn(convo);

                mockMvc.perform(post("/api/conversations").with(asUser())
                                .contentType(MediaType.APPLICATION_JSON).content("""
                                                {"title": "Hello"}
                                                """))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.status").value(201))
                                .andExpect(jsonPath("$.data.id").value(convoId.toString()));
        }

        @Test
        void create_blankTitle_returns400() throws Exception {
                mockMvc.perform(post("/api/conversations").with(asUser())
                                .contentType(MediaType.APPLICATION_JSON).content("""
                                                {"title": "   "}
                                                """))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(MessageKey.FIELD_REQUIRED));
                verifyNoInteractions(conversationService);
        }

        @Test
        void create_titleTooLong_returns400() throws Exception {
                mockMvc.perform(post("/api/conversations").with(asUser())
                                .contentType(MediaType.APPLICATION_JSON).content("""
                                                {"title": "%s"}
                                                """.formatted("a".repeat(201))))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(MessageKey.TITLE_TOO_LONG));
        }

        @Test
        void update_valid_returns200() throws Exception {
                when(conversationService.updateConversation(userId, convoId, "Renamed")).thenReturn(convo);

                mockMvc.perform(put("/api/conversations/{id}", convoId).with(asUser())
                                .contentType(MediaType.APPLICATION_JSON).content("""
                                                {"title": "Renamed"}
                                                """))
                                .andExpect(status().isOk());
                verify(conversationService).updateConversation(userId, convoId, "Renamed");
        }

        @Test
        void delete_returns204() throws Exception {
                mockMvc.perform(delete("/api/conversations/{id}", convoId).with(asUser()))
                                .andExpect(status().isNoContent());
                verify(conversationService).deleteConversation(userId, convoId);
        }

        @Test
        void delete_notFound_returns404() throws Exception {
                doThrow(new AppException(HttpStatus.NOT_FOUND, MessageKey.CONVERSATION_NOT_FOUND))
                                .when(conversationService).deleteConversation(any(), any());

                mockMvc.perform(delete("/api/conversations/{id}", convoId).with(asUser()))
                                .andExpect(status().isNotFound());
        }

        // ---------- messages ----------

        @Test
        void messages_returnsListForUser() throws Exception {
                Message msg = new Message(UUID.randomUUID(), "user", "Hi", null, Instant.parse("2026-01-01T00:00:00Z"));
                when(messageService.getMessages(userId, convoId)).thenReturn(List.of(msg));

                mockMvc.perform(get("/api/conversations/{id}/messages", convoId).with(asUser()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data[0].role").value("user"))
                                .andExpect(jsonPath("$.data[0].content").value("Hi"));
        }

        @Test
        void stream_blankContent_returns400() throws Exception {
                mockMvc.perform(post("/api/conversations/{id}/stream", convoId).with(asUser())
                                .contentType(MediaType.APPLICATION_JSON).content("""
                                                {"content": ""}
                                                """))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(MessageKey.FIELD_REQUIRED));
                verifyNoInteractions(streamingService);
        }

        @Test
        void get_invalidUuid_returns400() throws Exception {
                mockMvc.perform(get("/api/conversations/{id}", "not-a-uuid").with(asUser()))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(MessageKey.TYPE_MISSMATCH));
        }
}
