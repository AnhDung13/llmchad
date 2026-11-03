package com.llmchad;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Điểm khởi động của backend.
 *
 * @SpringBootApplication gồm 3 việc:
 * - @SpringBootConfiguration: class này cũng là một class cấu hình (@Configuration).
 * - @ComponentScan: quét package com.llmchad và các package con, tạo bean cho mọi
 *   @RestController, @Service, @Repository, @Component, @Configuration tìm thấy.
 * - @EnableAutoConfiguration: tự tạo sẵn bean dựa trên thư viện trong pom.xml
 *   (Tomcat, DataSource, JdbcClient, ObjectMapper, Spring Security...).
 *
 * Vì vậy mọi class của bạn phải nằm trong com.llmchad hoặc package con của nó.
 */
@SpringBootApplication
public class LlmchadApplication {

	public static void main(String[] args) {
		SpringApplication.run(LlmchadApplication.class, args);
	}

}
