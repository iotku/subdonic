package net.iotku.subdonic;

import net.iotku.subdonic.bot.TestBot;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestBot.class)
class SubdonicApplicationTests {

	@Test
	void contextLoads() {
	}

}
