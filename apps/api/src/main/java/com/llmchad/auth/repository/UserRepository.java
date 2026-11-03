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

    public void insertUser(String email, String passwordHash, String displayName) {
        String sql = """
                INSERT INTO users (email, password_hash, display_name)
                VALUES (:email, :passwordHash, :displayName)
                """;
        jdbcClient.sql(sql)
                .param("email", email)
                .param("passwordHash", passwordHash)
                .param("displayName", displayName).update();

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
