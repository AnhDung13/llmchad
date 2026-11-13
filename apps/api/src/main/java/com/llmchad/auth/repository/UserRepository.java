package com.llmchad.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import com.llmchad.auth.entity.UserEntity;
import com.llmchad.auth.model.User;
import com.llmchad.auth.model.UserCredentials;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    // Derived query: Spring Data đọc tên method để sinh câu SQL. existsBy... → SELECT ... LIMIT 1
    boolean existsByEmail(String email);

    Optional<UserEntity> findByEmail(String email);

    /**
     * Kiểu trả về là record User chứ không phải entity (DTO projection): Spring Data chỉ SELECT 4 cột khớp tên
     * tham số của record và gọi constructor của nó. Chữ "User" giữa find và By chỉ để đặt tên, Spring Data bỏ qua.
     * Không dùng findById có sẵn được vì method đó trả entity.
     */
    Optional<User> findUserById(UUID id);

    /**
     * default method: code Java bình thường trong interface, gọi lại method khác của repository.
     * JPQL không lồng được "new UserCredentials(new User(...), ...)", nên load entity rồi tự chuyển.
     */
    default Optional<UserCredentials> findCredentialsByEmail(String email) {
        return findByEmail(email).map(UserEntity::toCredentials);
    }

    // Hai câu UPDATE dưới đây giữ nguyên SQL cũ (nativeQuery = true) thay vì load entity rồi sửa field:
    // mỗi câu đọc giá trị CŨ của email_verified_at và ghi mới trong cùng một lệnh nên không bị race giữa
    // hai request, và logic bảo mật này đã có test.
    //
    // @Modifying: báo cho Spring Data đây là UPDATE/DELETE (gọi executeUpdate), không phải SELECT.
    // flushAutomatically: ghi các thay đổi entity đang chờ xuống DB trước khi chạy câu này.
    // clearAutomatically: câu SQL đổi DB trực tiếp, entity Hibernate đang giữ trong bộ nhớ thành cũ → xoá hết,
    // lần đọc sau mới lấy từ DB.
    // @Transactional: method tự khai báo trong interface không có transaction mặc định, mà UPDATE thì bắt buộc
    // phải có. Đang ở trong transaction của service thì dùng chung transaction đó.

    // Google/GitHub vừa xác nhận người này là chủ email → đánh dấu user đã xác minh.
    // Nếu trước đó user CHƯA từng xác minh (tài khoản tạo trước khi có bước xác minh email), mật khẩu có thể do
    // kẻ gian đặt khi đăng ký bằng email của người khác → xoá mật khẩu. Đã xác minh rồi thì giữ nguyên mật khẩu.
    // CASE đọc giá trị email_verified_at CŨ (trước khi câu lệnh này đổi nó).
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE users
            SET password_hash = CASE WHEN email_verified_at IS NULL THEN NULL ELSE password_hash END,
                email_verified_at = COALESCE(email_verified_at, now())
            WHERE id = :userId
            """)
    void markEmailVerifiedByProvider(UUID userId);

    // Đặt lại mật khẩu qua link trong mail = người này vừa chứng minh sở hữu email → đánh dấu đã xác minh luôn.
    // Không đánh dấu thì lần đăng nhập Google/GitHub sau, markEmailVerifiedByProvider sẽ xoá mật khẩu vừa đặt.
    // Trả về số dòng đã sửa: 0 = không có user này (đã bị xoá)
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE users
            SET password_hash = :passwordHash,
                email_verified_at = COALESCE(email_verified_at, now())
            WHERE id = :userId
            """)
    int updatePassword(UUID userId, String passwordHash);
}
