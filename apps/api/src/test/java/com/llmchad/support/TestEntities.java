package com.llmchad.support;

import org.springframework.test.util.ReflectionTestUtils;

import com.llmchad.auth.entity.UserEntity;
import com.llmchad.auth.model.User;

/**
 * Entity giống như vừa lưu vào DB, dùng làm giá trị trả về của repository giả (mock).
 * id và createdAt bình thường do Hibernate/DB gán nên entity không có setter: test gán bằng reflection.
 */
public final class TestEntities {

    private TestEntities() {
    }

    public static UserEntity savedUser(User user, String passwordHash) {
        UserEntity entity = new UserEntity(user.email(), passwordHash, user.displayName());
        ReflectionTestUtils.setField(entity, "id", user.id());
        ReflectionTestUtils.setField(entity, "createdAt", user.createdAt());
        return entity;
    }
}
