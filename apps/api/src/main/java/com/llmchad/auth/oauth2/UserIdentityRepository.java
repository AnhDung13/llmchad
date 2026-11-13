package com.llmchad.auth.oauth2;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Spring Data tự tạo class cài đặt interface này lúc khởi động (không cần @Repository).
 * JpaRepository<Entity, kiểu id> có sẵn save, saveAndFlush, findById, delete...
 */
public interface UserIdentityRepository extends JpaRepository<UserIdentityEntity, Long> {

    /**
     * Chỉ lấy một cột (user_id) nên viết JPQL: truy vấn trên entity và field Java, không phải tên bảng/cột.
     * :provider khớp với tên tham số của method.
     */
    @Query("""
            SELECT i.userId FROM UserIdentityEntity i
            WHERE i.provider = :provider AND i.providerUserId = :providerUserId
            """)
    Optional<UUID> findUserId(String provider, String providerUserId);
}
