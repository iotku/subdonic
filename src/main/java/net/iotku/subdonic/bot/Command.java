package net.iotku.subdonic.bot;

import discord4j.core.event.domain.message.MessageCreateEvent;
import reactor.core.publisher.Mono;

import java.io.IOException;

public interface Command {
    Mono<Void> execute(MessageCreateEvent event, String[] args) throws IOException, InterruptedException;
}
