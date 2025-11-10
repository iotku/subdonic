package net.iotku.subdonic.api.v1.dto;

import discord4j.core.object.entity.Guild;

public record DiscordGuild(Long id, String name, int memberCount) {
    public static DiscordGuild fromNativeGuild(Guild guild) {
        return new DiscordGuild(
                guild.getId().asLong(),
                guild.getName(),
                guild.getMemberCount()
        );
    }
}