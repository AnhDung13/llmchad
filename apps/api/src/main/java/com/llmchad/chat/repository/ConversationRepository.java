package com.llmchad.chat.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import com.llmchad.chat.entity.ConversationEntity;
import com.llmchad.chat.model.Conversation;

public interface ConversationRepository extends JpaRepository<ConversationEntity, UUID> {

    /**
     * WHERE id = ? AND user_id = ?, trả thẳng record Conversation (DTO projection, xem UserRepository.findUserById).
     * Projection đọc thẳng từ DB, không đi qua entity Hibernate đang giữ trong bộ nhớ.
     */
    Optional<Conversation> findByIdAndUserId(UUID id, UUID userId);

    /** Pageable thêm LIMIT/OFFSET. Trả List thay vì Page nên Spring Data không chạy thêm câu COUNT(*) */
    List<Conversation> findByUserIdOrderByModifiedAtDesc(UUID userId, Pageable pageable);

    /**
     * UPDATE thẳng, không load entity, nên @Generated của modifiedAt không chạy: phải tự gán.
     * Native SQL thay vì JPQL vì CURRENT_TIMESTAMP trong JPQL bị Hibernate dịch thành localtimestamp (giờ không kèm
     * múi giờ, Postgres phải đổi lại theo múi giờ của session), còn now() trả đúng thời điểm như mọi chỗ khác.
     * Xem UserRepository về @Modifying và @Transactional.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = "UPDATE conversations SET modified_at = now() WHERE id = :id")
    void touch(UUID id);

    /**
     * Có @Query nên Spring Data không đọc tên method nữa. Không dùng derived delete (bỏ @Query): kiểu đó SELECT
     * entity ra rồi xoá từng cái. Ở đây một câu DELETE, trả số dòng đã xoá (0 = không có hoặc không phải của user).
     * Tin nhắn bị xoá theo nhờ ON DELETE CASCADE trong DB.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ConversationEntity c WHERE c.id = :id AND c.userId = :userId")
    int deleteByIdAndUserId(UUID id, UUID userId);
}
