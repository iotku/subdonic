package net.iotku.subdonic.bot;

import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.discordjson.json.ApplicationInfoData;
import discord4j.discordjson.possible.Possible;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import net.iotku.subdonic.ApiClient.Status;


@Component
@SuppressWarnings("unused") // SpringBoot loads this via the @Component annotation
public class Bot {
    private static final int MAX_RETRY = 5; // Most times we should attempt to repeat a network action
    private static final Logger logger = LoggerFactory.getLogger(Bot.class);
    private final Commands commands;

    private static GatewayDiscordClient client;
    private Long ownerId; // The owner of the bot according to Discord
    private final String DISCORD_TOKEN;

    public Bot(@Value("${discord.token}") String token) {
            // NOTE: Must have "Message Content Intent" enabled in developer dev portal bot settings
            this.commands = new Commands(this);
            this.DISCORD_TOKEN = token;
        }

        // NOTE: We use this EventListener so we ensure that our webserver is ready before starting the bot
        //       If not, we would have failures updating the status API endpoints
        @EventListener(ApplicationReadyEvent.class)
        private void init() {
            if (DISCORD_TOKEN == null) { // This should only be null when testing
                System.err.println("DISCORD_TOKEN was null, hopefully we're running tests...");
                return;
            }
            client = DiscordClient.create(DISCORD_TOKEN)
                    .gateway()
                    .setEnabledIntents(IntentSet.nonPrivileged().or(IntentSet.of(Intent.MESSAGE_CONTENT)))
                    .login()
                    .block();
            assert client != null;
            handleEvents();
            fetchOwnerId();
        }

    private void handleEvents() {
        client.on(ReadyEvent.class).subscribe(event -> logger.info("Discord client is ready"));

        client.on(GuildCreateEvent.class).subscribe(event -> {
            logger.info(event.getGuild().toString());
            try {
                Status.addGuild(event.getGuild());
            } catch (IOException | InterruptedException e) {
                System.out.println("Failed to add guild status" + e);
            }
        });
        // React to command chat messages
        client.getEventDispatcher().on(MessageCreateEvent.class)
                .flatMap(event -> {
                    String content = event.getMessage().getContent();
                    if (!commands.isCommand(event)) return Mono.empty(); // exit early

                    // args[0] is the command
                    String[] args = commands.stripCommandPrefixOrMentions(event).trim().split("\\s+");
                    if (args.length == 0) return Mono.empty();

                    String[] cmdArgs = Arrays.copyOfRange(args, 1, args.length);
                    logger.info("Attempting to run command: {}", Arrays.toString(args));
                    return Optional.ofNullable(Commands.get(args[0].toLowerCase()))
                            .map(command -> {
                                try {
                                    return command.execute(event, cmdArgs);
                                } catch (IOException | InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                            })
                            .orElse(Mono.empty()); // TODO: Maybe add some user feedback that the command was not found
                }).subscribe();
    }

    private void fetchOwnerId() {
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            this.ownerId = client.rest().getApplicationInfo()
                    .map(ApplicationInfoData::owner)
                    .map(Possible::toOptional)
                    .flatMap(Mono::justOrEmpty)
                    .map(user -> Snowflake.asLong(user.id()))
                    .timeout(Duration.ofSeconds(10))
                    .doOnError(e -> logger.info("Failed to fetch owner ID: {}", e.getMessage()))
                    .block();
            if (this.ownerId != null && this.ownerId != 0) break;
        }

        if (this.ownerId == null || this.ownerId == 0) {
            throw new RuntimeException("Could not determine ownerId");
        }

        logger.info("OwnerId set to: {}", ownerId);
    }

    public Long getOwnerId() {
        return this.ownerId;
    }

    public static GatewayDiscordClient getClient() {
        return client;
    }
}