package net.iotku.subdonic.bot;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Member;
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

    public static MessageCtx buildCtx(MessageCreateEvent event) {
        return new MessageCtx(event.getGuildId().orElse(null), event.getMessage().getChannelId(), event.getMember().map(Member::getId).orElse(null));
    }
}
