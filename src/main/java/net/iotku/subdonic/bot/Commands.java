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
    private static final char DEFAULT_ACTION_CHAR = '!';
    private static final Map<Snowflake, Character> guildActionChars = new ConcurrentHashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(Commands.class);
    private static final Map<String, Command> commands = new HashMap<>();

    private boolean messageIsAdmin(MessageCreateEvent event) { // TODO: There's probably a neater way to do this
        return event.getMessage().getAuthor().isPresent() && event.getMessage().getAuthor().get().getId().asLong() == Bot.getOwnerId();
    }

    static {
        commands.put("ping", event -> Objects.requireNonNull(event.getMessage().getChannel().block()).createMessage("Pong!").then());
        commands.put("pong", event -> Objects.requireNonNull(event.getMessage().getChannel().block()).createMessage("Ping!").then());
        commands.put("join", event -> Mono.justOrEmpty(event.getMember())
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

    public static Map<String, Command> get() {
        return commands;
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static char getActionChar(Optional<Snowflake> guildId) {
        if (guildId.isPresent()) {
            return guildActionChars.getOrDefault(guildId.get(), DEFAULT_ACTION_CHAR);
        }
        return DEFAULT_ACTION_CHAR;
    }

    public static void setActionChar(Snowflake guildId, char actionChar) {
        guildActionChars.put(guildId, actionChar);
        logger.info("Set {} action char to {}", guildId, actionChar);
    }
}
