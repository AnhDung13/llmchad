package com.llmchad.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.llmchad.chat.model.Conversation;
import com.llmchad.common.Paging;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ConversationRepository.class)
class ConversationRepositoryTest {

    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private JdbcClient jdbcClient;

    private UUID owner;
    private UUID otherUser;

    @BeforeEach
    void setUp() {
        owner = insertUser();
        otherUser = insertUser();
    }

    // Trong 1 transaction now() luôn trả cùng một giá trị, nên test thứ tự phải tự đặt modified_at
    private void setModifiedAt(UUID convoId, Instant time) {
        jdbcClient.sql("UPDATE conversations SET modified_at = :t WHERE id = :id")
                .param("t", Timestamp.from(time)).param("id", convoId).update();
    }

    private UUID insertUser() {
        return jdbcClient.sql("""
                INSERT INTO users (email, password_hash, display_name)
                VALUES (:email, 'hash', 'Test') RETURNING id
                """)
                .param("email", "user-" + UUID.randomUUID() + "@example.com")
                .query(UUID.class).single();
    }

    @Test
    void insert_returnsCreatedRow() {
        Conversation convo = conversationRepository.insertConversation(owner, "Hello");

        assertThat(convo.id()).isNotNull();
        assertThat(convo.title()).isEqualTo("Hello");
        assertThat(convo.createdAt()).isNotNull();
        assertThat(convo.modifiedAt()).isNotNull();
    }

    @Test
    void findById_onlyReturnsOwnersConversation() {
        Conversation convo = conversationRepository.insertConversation(owner, "Hello");

        assertThat(conversationRepository.findById(owner, convo.id())).contains(convo);
        assertThat(conversationRepository.findById(otherUser, convo.id())).isEmpty();
    }

    @Test
    void getConversations_filtersByUserAndSortsByModifiedAtDesc() {
        Conversation oldest = conversationRepository.insertConversation(owner, "oldest");
        Conversation newest = conversationRepository.insertConversation(owner, "newest");
        Conversation middle = conversationRepository.insertConversation(owner, "middle");
        conversationRepository.insertConversation(otherUser, "not mine");
        setModifiedAt(oldest.id(), Instant.parse("2026-01-01T00:00:00Z"));
        setModifiedAt(middle.id(), Instant.parse("2026-01-02T00:00:00Z"));
        setModifiedAt(newest.id(), Instant.parse("2026-01-03T00:00:00Z"));

        List<Conversation> result = conversationRepository.getConversations(owner, new Paging(20, 1));

        assertThat(result).extracting(Conversation::title).containsExactly("newest", "middle", "oldest");
    }

    @Test
    void getConversations_paginates() {
        for (int i = 1; i <= 5; i++) {
            Conversation c = conversationRepository.insertConversation(owner, "c" + i);
            setModifiedAt(c.id(), Instant.parse("2026-01-0" + i + "T00:00:00Z"));
        }

        assertThat(conversationRepository.getConversations(owner, new Paging(2, 1)))
                .extracting(Conversation::title).containsExactly("c5", "c4");
        assertThat(conversationRepository.getConversations(owner, new Paging(2, 2)))
                .extracting(Conversation::title).containsExactly("c3", "c2");
        assertThat(conversationRepository.getConversations(owner, new Paging(2, 3)))
                .extracting(Conversation::title).containsExactly("c1");
        assertThat(conversationRepository.getConversations(owner, new Paging(2, 4))).isEmpty();
    }

    @Test
    void update_ownConversation_changesTitle() {
        Conversation convo = conversationRepository.insertConversation(owner, "Hello");

        assertThat(conversationRepository.updateConversation(owner, convo.id(), "Renamed"))
                .get().extracting(Conversation::title).isEqualTo("Renamed");
    }

    @Test
    void update_otherUsersConversation_isEmptyAndUnchanged() {
        Conversation convo = conversationRepository.insertConversation(owner, "Hello");

        assertThat(conversationRepository.updateConversation(otherUser, convo.id(), "Hacked")).isEmpty();
        assertThat(conversationRepository.findById(owner, convo.id()))
                .get().extracting(Conversation::title).isEqualTo("Hello");
    }

    @Test
    void touchConversation_bumpsModifiedAt() {
        Conversation convo = conversationRepository.insertConversation(owner, "Hello");
        Instant old = Instant.parse("2020-01-01T00:00:00Z");
        setModifiedAt(convo.id(), old);

        conversationRepository.touchConversation(convo.id());

        assertThat(conversationRepository.findById(owner, convo.id()).orElseThrow().modifiedAt()).isAfter(old);
    }

    @Test
    void delete_otherUsersConversation_returnsFalseAndKeepsRow() {
        Conversation convo = conversationRepository.insertConversation(owner, "Hello");

        assertThat(conversationRepository.deleteConversation(otherUser, convo.id())).isFalse();
        assertThat(conversationRepository.findById(owner, convo.id())).isPresent();
    }

    @Test
    void delete_ownConversation_returnsTrueAndRemovesRow() {
        Conversation convo = conversationRepository.insertConversation(owner, "Hello");

        assertThat(conversationRepository.deleteConversation(owner, convo.id())).isTrue();
        assertThat(conversationRepository.findById(owner, convo.id())).isEmpty();
    }
}
