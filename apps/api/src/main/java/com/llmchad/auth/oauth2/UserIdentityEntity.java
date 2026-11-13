package com.llmchad.auth.oauth2;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Một tài khoản Google/GitHub đã liên kết với user (bảng user_identities).
 *
 * user_id map thành UUID thường, không dùng @ManyToOne UserEntity: chỉ cần id để insert/tìm,
 * không phải load cả user. Khoá ngoại vẫn do DB kiểm tra.
 *
 * created_at không map: không đọc tới bao giờ, và Hibernate không đưa nó vào INSERT nên DB tự điền DEFAULT now().
 */
@Entity
@Table(name = "user_identities")
public class UserIdentityEntity {

    // Cột id là GENERATED ALWAYS AS IDENTITY: DB sinh số, Hibernate không gửi id trong INSERT mà đọc lại sau đó
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID userId;

    private String provider;

    private String providerUserId;

    protected UserIdentityEntity() {
    }

    public UserIdentityEntity(UUID userId, String provider, String providerUserId) {
        this.userId = userId;
        this.provider = provider;
        this.providerUserId = providerUserId;
    }

    public Long getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }
}
