package com.llmchad.auth.oauth2;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserIdentityRepository {
    private final JdbcClient jdbcClient;

    public UserIdentityRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<UUID> findUserId(String provider, String providerUserId) {
        String sql = "SELECT user_id FROM user_identities WHERE provider = :provider AND provider_user_id = :providerUserId";

        return jdbcClient.sql(sql).param("provider", provider).param("providerUserId", providerUserId)
                .query((rs, rowNum) -> rs.getObject("user_id", UUID.class)).optional();
    }

    public void insert(UUID userId, String provider, String providerUserId) {
        String sql = "INSERT INTO user_identities (user_id, provider, provider_user_id) VALUES (:userId,:provider,:providerUserId)";
        jdbcClient.sql(sql).param("userId", userId).param("provider", provider).param("providerUserId", providerUserId)
                .update();
    }
}
