package com.llmchad.auth.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.llmchad.auth.model.User;
import com.llmchad.auth.model.UserCredentials;

@Repository
public class UserRepository {
    private final JdbcClient jdbcClient;

    public UserRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Boolean existEmail(String email) {
        String sql = "SELECT EXISTS(SELECT 1 FROM users WHERE email = :email)";
        return jdbcClient.sql(sql).param("email", email).query(Boolean.class).single();
    }

    public Optional<UserCredentials> findCredentialsByEmail(String email) {
        String sql = "SELECT id, email, display_name, created_at, password_hash FROM users WHERE email = :email";
        return jdbcClient.sql(sql).param("email", email)
                .query((rs, rowNum) -> new UserCredentials(mapRow(rs, rowNum), rs.getString("password_hash")))
                .optional();
    }

    // RETURNING: lấy luôn user vừa tạo (id, created_at do DB sinh), không phải
    // SELECT thêm lần nữa
    public User insertUser(String email, String passwordHash, String displayName) {
        String sql = """
                INSERT INTO users (email, password_hash, display_name)
                VALUES (:email, :passwordHash, :displayName)
                RETURNING id, email, display_name, created_at
                """;
        return jdbcClient.sql(sql)
                .param("email", email)
                .param("passwordHash", passwordHash)
                .param("displayName", displayName)
                .query(UserRepository::mapRow)
                .single();
    }

    // Google/GitHub vừa xác nhận người này là chủ email → đánh dấu user đã xác minh.
    // Nếu trước đó user CHƯA từng xác minh (tài khoản tạo trước khi có bước xác minh email), mật khẩu có thể do
    // kẻ gian đặt khi đăng ký bằng email của người khác → xoá mật khẩu. Đã xác minh rồi thì giữ nguyên mật khẩu.
    // Một câu UPDATE: CASE đọc giá trị email_verified_at CŨ (trước khi câu lệnh này đổi nó).
    public void markEmailVerifiedByProvider(UUID userId) {
        String sql = """
                UPDATE users
                SET password_hash = CASE WHEN email_verified_at IS NULL THEN NULL ELSE password_hash END,
                    email_verified_at = COALESCE(email_verified_at, now())
                WHERE id = :userId
                """;
        jdbcClient.sql(sql).param("userId", userId).update();
    }

    // Đặt lại mật khẩu qua link trong mail = người này vừa chứng minh sở hữu email → đánh dấu đã xác minh luôn.
    // Không đánh dấu thì lần đăng nhập Google/GitHub sau, markEmailVerifiedByProvider sẽ xoá mật khẩu vừa đặt.
    // false = không có user này (đã bị xoá)
    public boolean updatePassword(UUID userId, String passwordHash) {
        String sql = """
                UPDATE users
                SET password_hash = :passwordHash,
                    email_verified_at = COALESCE(email_verified_at, now())
                WHERE id = :userId
                """;
        return jdbcClient.sql(sql).param("passwordHash", passwordHash).param("userId", userId).update() == 1;
    }

    public Optional<User> findById(UUID id) {
        String sql = "SELECT id, email, display_name, created_at FROM users WHERE id = :id";
        return jdbcClient.sql(sql)
                .param("id", id)
                .query(UserRepository::mapRow)
                .optional();
    }

    private static User mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new User(
                rs.getObject("id", UUID.class),
                rs.getString("email"),
                rs.getString("display_name"),
                rs.getTimestamp("created_at").toInstant());
    }

}
