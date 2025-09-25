package net.iotku.subdonic.bot;

import discord4j.common.util.Snowflake;
import reactor.util.annotation.NonNull;

public record MessageCtx(Snowflake guildId, Snowflake channelId, Snowflake memberId) {
    @Override
    public @NonNull String toString() {
        return "MessageCtx{" +
                "guildId=" + guildId.asLong() +
                ", channelId=" + channelId.asLong() +
                ", memberId=" + memberId.asLong() +
                '}';
    }
}
