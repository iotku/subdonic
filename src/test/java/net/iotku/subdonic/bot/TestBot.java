package net.iotku.subdonic.bot;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import static org.mockito.Mockito.mock;

@TestConfiguration
@SuppressWarnings("unused")
public class TestBot {
    @Bean
    public Bot bot() {
        return mock(Bot.class); // Mock the discord bot class as we can't verify tokens in CI
    }
}