package com.llmchad.auth.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;

import com.llmchad.auth.model.User;
import com.llmchad.auth.model.UserCredentials;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Một dòng của bảng users. Schema vẫn do Flyway tạo; spring.jpa.hibernate.ddl-auto=validate chỉ kiểm tra
 * mapping dưới đây khớp với bảng thật lúc app khởi động.
 *
 * Không dùng record được: Hibernate cần constructor rỗng và field không final để tự gán giá trị đọc từ DB.
 * Record User / UserCredentials vẫn là thứ service trả ra ngoài (toModel / toCredentials).
 *
 * Tên cột tự suy từ tên field: passwordHash → password_hash (Spring Boot đổi camelCase sang snake_case).
 * Cột email_verified_at cố ý không map: Hibernate không đưa nó vào INSERT nên DB tự điền DEFAULT now(),
 * còn các câu UPDATE đụng tới nó viết bằng SQL trong UserRepository.
 */
@Entity
@Table(name = "users")
public class UserEntity {

    // Hibernate tự sinh UUID ngay lúc persist (trong Java), không cần đợi DB trả về
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String email;

    private String passwordHash;

    private String displayName;

    // @Generated: giá trị do DB sinh. Hibernate không đưa cột vào INSERT (DB tự điền DEFAULT now() trong migration)
    // rồi đọc giá trị về field này. Trên Postgres là INSERT ... RETURNING created_at, không SELECT thêm.
    // updatable = false: @Generated chỉ bỏ cột khỏi INSERT, còn UPDATE vẫn ghi lại giá trị cũ nếu không chặn
    @Generated
    @Column(updatable = false)
    private Instant createdAt;

    // JPA bắt buộc có constructor không tham số. protected: code của mình không gọi nhầm được
    protected UserEntity() {
    }

    public UserEntity(String email, String passwordHash, String displayName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
    }

    public User toModel() {
        return new User(id, email, displayName, createdAt);
    }

    public UserCredentials toCredentials() {
        return new UserCredentials(toModel(), passwordHash);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
