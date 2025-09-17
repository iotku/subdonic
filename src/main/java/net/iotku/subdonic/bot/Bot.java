package net.iotku.subdonic.bot;

import discord4j.core.DiscordClient;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.discordjson.json.gateway.Ready;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Bot {
    private GatewayDiscordClient client;

    public Bot(@Value("${discord.token}") String token) {
        this.client = DiscordClientBuilder.create(token).build().login().block();

        assert client != null;
        client.on(ReadyEvent.class).subscribe(event -> System.out.println("Discord client is ready"));
        client.on(MessageCreateEvent.class).subscribe(event -> {
            Message message = event.getMessage();
            System.out.println("Bot recieved msg: " + message);
            if ("!ping".equals(message.getContent())) {
                MessageChannel channel = message.getChannel().block();
                channel.createMessage("Pong!").block();
            }
        });
    }
}