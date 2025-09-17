package net.iotku.subdonic.bot;

import discord4j.core.DiscordClient;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Bot {
    private static final Logger logger = LoggerFactory.getLogger(Bot.class);

    public Bot(@Value("${discord.token}") String token) {
        GatewayDiscordClient client = DiscordClientBuilder.create(token).build().login().block();
        // NOTE: Must have "Message Content Intent" enabled in developer dev portal bot settings
        assert client != null;
        client.on(ReadyEvent.class).subscribe(event -> logger.info("Discord client is ready"));
        client.on(MessageCreateEvent.class).subscribe(event -> {
            Message message = event.getMessage();
            logger.info("Bot recieved msg: {}", message);
            if ("!ping".equals(message.getContent())) {
                MessageChannel channel = message.getChannel().block();
                assert channel != null;
                channel.createMessage("Pong!").block();
            }
        });
    }
}