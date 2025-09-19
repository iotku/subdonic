package net.iotku.subdonic.bot;

import discord4j.common.util.Snowflake;

public record MessageCtx(Snowflake guildId, Snowflake channelId, Snowflake memberId) {
}
