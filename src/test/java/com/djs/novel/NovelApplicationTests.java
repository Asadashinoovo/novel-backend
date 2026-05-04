package com.djs.novel;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootTest
class NovelApplicationTests {

	@Test
	void contextLoads() {
		BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
		String rawPassword = "123456";
		String encoded = encoder.encode(rawPassword);
		System.out.println("===== BCrypt 加密密码 =====");
		System.out.println("明文: " + rawPassword);
		System.out.println("密文: " + encoded);
		System.out.println("=========================");
		System.out.println("SQL: INSERT INTO user (username, password) VALUES ('admin', '" + encoded + "');");
	}

}
