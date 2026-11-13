package com.llmchad.config;

import javax.sql.DataSource;

import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;

/**
 * Mặc định Spring đổi mọi lỗi ràng buộc của Hibernate (trùng khoá, khoá ngoại, CHECK...) thành cùng một loại
 * DataIntegrityViolationException. Gắn bộ dịch lỗi SQL mà JdbcClient đang dùng để lỗi trùng khoá (Postgres 23505)
 * ra đúng DuplicateKeyException như trước: AuthService và OAuthLoginSuccessHandler bắt riêng loại này, không
 * nhầm với lỗi khác.
 *
 * @DataJpaTest không quét class @Configuration của mình, test repository phải @Import(JpaConfig.class).
 */
@Configuration(proxyBeanMethods = false)
public class JpaConfig {

    public JpaConfig(JpaVendorAdapter jpaVendorAdapter, DataSource dataSource) {
        if (jpaVendorAdapter.getJpaDialect() instanceof HibernateJpaDialect dialect) {
            dialect.setJdbcExceptionTranslator(new SQLErrorCodeSQLExceptionTranslator(dataSource));
        }
    }
}
