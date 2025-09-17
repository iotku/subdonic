package net.iotku.subdonic.bot;

import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.discordjson.json.ApplicationInfoData;
import discord4j.discordjson.possible.Possible;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;


@Component
@SuppressWarnings("unused") // SpringBoot loads this via the @Component annotation
public class Bot {
    private static final int MAX_RETRY = 5; // Most times we should attempt to repeat a network action
    private static final Logger logger = LoggerFactory.getLogger(Bot.class);

    private final GatewayDiscordClient client;
    private static Long ownerId; // The owner of the bot according to Discord

    public Bot(@Value("${discord.token}") String token) {
        // NOTE: Must have "Message Content Intent" enabled in developer dev portal bot settings
        client = DiscordClientBuilder.create(token).build().login().block();
        assert client != null;
        handleEvents();
        fetchOwnerId();
    }

    private void handleEvents() {
        client.on(ReadyEvent.class).subscribe(event -> logger.info("Discord client is ready"));

        // React to command chat messages
        client.getEventDispatcher().on(MessageCreateEvent.class)
                .flatMap(event -> Mono.just(event.getMessage().getContent())
                        .flatMap(content -> Flux.fromIterable(Commands.get().entrySet())
                                .filter(entry -> content.startsWith(Commands.getActionChar(event.getGuildId()) + entry.getKey()))
                                .flatMap(entry -> entry.getValue().execute(event))
                                .next()))
                .subscribe();
    }

    private void fetchOwnerId() {
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            ownerId = client.rest().getApplicationInfo()
                    .map(ApplicationInfoData::owner)
                    .map(Possible::toOptional)
                    .flatMap(Mono::justOrEmpty)
                    .map(user -> Snowflake.asLong(user.id()))
                    .timeout(Duration.ofSeconds(10))
                    .doOnError(e -> logger.info("Failed to fetch owner ID: {}", e.getMessage()))
                    .block();
            if (ownerId != null && ownerId != 0) break;
        }

        if (ownerId == null || ownerId == 0) {
            throw new RuntimeException("Could not determine ownerId");
        }

        logger.info("OwnerId set to: {}", ownerId);
    }

    public static Long getOwnerId() {
        return ownerId;
    }
}