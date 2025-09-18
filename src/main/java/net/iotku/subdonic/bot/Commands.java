package net.iotku.subdonic.bot;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
import discord4j.core.spec.VoiceChannelJoinSpec;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class Commands {
    private static final String DEFAULT_ACTION_STR = "!";
    private static final Map<Snowflake, String> guildActionStrs = new ConcurrentHashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(Commands.class);
    private static final Map<String, Command> COMMANDS = new HashMap<>();
    private final Bot instance;
    public Commands(Bot instance) {
        this.instance = instance;
    }
    private boolean messageIsAdmin(MessageCreateEvent event) { // TODO: There's probably a neater way to do this
        return event.getMessage().getAuthor().isPresent() && event.getMessage().getAuthor().get().getId().asLong() == instance.getOwnerId();
    }

    static {
        register("ping", (event, args) -> Objects.requireNonNull(event.getMessage().getChannel().block()).createMessage("Pong!").then());
        register("pong", (event, args) -> Objects.requireNonNull(event.getMessage().getChannel().block()).createMessage("Ping!").then());
        register("join", (event, args) -> Mono.justOrEmpty(event.getMember())
                .flatMap(Member::getVoiceState)
                .flatMap(VoiceState::getChannel)
                .flatMap(channel -> {
                    GuildAudioManager manager = GuildAudioManager.of(channel.getGuildId());
                    return channel.join(VoiceChannelJoinSpec.builder().provider(manager.getProvider()).selfDeaf(true).build())
                            .doOnNext(vc -> {
                                logger.info("Joined voice channel {}", vc.getChannelId());

                                GuildAudioManager.getPlayerManager().loadItem("music/sample-12s.mp3", new AudioLoadResultHandler() {
                                    @Override
                                    public void trackLoaded(AudioTrack track) {
                                        logger.info("Loading: {}", track.getInfo().uri);
                                        manager.getPlayer().startTrack(track, false);
                                    }

                                    @Override
                                    public void playlistLoaded(AudioPlaylist playlist) {
                                    }

                                    @Override
                                    public void noMatches() {
                                    }

                                    @Override
                                    public void loadFailed(FriendlyException exception) {
                                    }
                                });
                            });
                }).doOnError(e -> logger.info("VOICE error: {}", e.getMessage()))
                .then());
    }

    public static void register(String name, Command command) {
        COMMANDS.put(name.toLowerCase(), command);
    }

    public static Command get(String command) {
        return COMMANDS.get(command);
    }

    public boolean isCommand(String content, MessageCreateEvent event) {
        return content.startsWith(Commands.getActionStr(event.getGuildId()))
                || event.getMessage().getUserMentionIds().contains(this.instance.getClient().getSelfId());
    }

    public String stripCommandPrefixOrMentions(String content, MessageCreateEvent event) {
        String prefix = getActionStr(event.getGuildId());
        if (content.startsWith(prefix)) {
            return content.substring(prefix.length()).trim();
        }

        // Remove self-mentions from message
        if (event.getMessage().getUserMentionIds().contains(this.instance.getClient().getSelfId())) {
            String selfId= this.instance.getClient().getSelfId().asString();
            String str1 = "<@" + selfId + ">";
            String str2 = "<@!" + selfId + ">";
            return content.replace(str1, "").replace(str2, "").trim();
        }

        return content; // fallback to original message
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static String getActionStr(Optional<Snowflake> guildId) {
        if (guildId.isPresent()) {
            return guildActionStrs.getOrDefault(guildId.get(), DEFAULT_ACTION_STR);
        }
        return DEFAULT_ACTION_STR;
    }

    public static void setActionStr(Snowflake guildId, String actionStr) {
        guildActionStrs.put(guildId, actionStr);
        logger.info("Set {} action char to {}", guildId, actionStr);
    }
}
