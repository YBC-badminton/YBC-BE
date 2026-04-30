package com.ybc.ybc;

import com.ybc.ybc.support.TestMailConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestMailConfig.class)
class YbcApplicationTests {

	@Test
	void contextLoads() {
	}

}
