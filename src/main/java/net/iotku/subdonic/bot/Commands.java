package net.iotku.subdonic.bot;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.PartialMember;
import discord4j.voice.VoiceConnection;

import net.iotku.subdonic.api.v1.dto.Song;
import net.iotku.subdonic.client.Search;
import net.iotku.subdonic.client.Stream;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Commands {
    private static final String DEFAULT_PREFIX = "!";
    private static final Map<Snowflake, String> guildPrefixes = new ConcurrentHashMap<>();
    private static final Logger log = LoggerFactory.getLogger(Commands.class);
    private static final Map<String, Command> COMMANDS = new HashMap<>();
    private final Bot instance;

    public Commands(Bot instance) {
        this.instance = instance;
    }

    /**
     * The message event was sent by the instance specified Owner of the Bot (as determined by the Discord API)
     * @param event a MessageCreateEvent
     * @return true if the message was sent by The Owner, false otherwise
     */
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
                    // Set lastTextChannel so we know where to put now playing messages
                    manager.setLastTextChannel(event.getMessage().getChannelId());
                    return manager.joinAndTrack(channel).then();
                }));

        register("disconnect", (event, args) -> {
            Snowflake guildId = event.getGuildId().orElse(null);
            if (guildId == null) return Mono.empty(); // DMs

            GuildAudioManager manager = GuildAudioManager.of(guildId);
            return Mono.justOrEmpty(manager.getConnection())
                    .flatMap(VoiceConnection::disconnect)
                    .then(event.getMessage()
                            .getChannel()
                            .flatMap(ch -> ch.createMessage("Disconnected from voice channel.")))
                    .then();
        });

        register("play", (event, args) -> Mono.justOrEmpty(event.getMember())
                .flatMap(Member::getVoiceState)
                .flatMap(VoiceState::getChannel)
                .flatMap(userChannel -> Mono.justOrEmpty(event.getGuildId())
                        .flatMap(guildId -> event.getClient().getSelfMember(guildId)
                                .flatMap(PartialMember::getVoiceState)
                                .flatMap(VoiceState::getChannel)
                                .flatMap(botChannel -> {
                                    if (botChannel.getId().equals(userChannel.getId())) { // bot in same channel
                                        return Mono.just(true);
                                    }
                                    return Mono.just(false); // bot in a different channel
                                })
                                // bot not in any channel, Join User's channel
                                .switchIfEmpty(GuildAudioManager.of(guildId).joinAndTrack(userChannel).thenReturn(true))
                        )
                )
                .flatMap(sameChannel -> {
                    if (!sameChannel) {
                        return event.getMessage().getChannel()
                                .flatMap(ch ->
                                        ch.createMessage("Must be in same voice channel to play music!")).then();
                    }

                    if (event.getGuildId().isEmpty()) return Mono.empty(); // do nothing in DMs
                    MessageCtx context = new MessageCtx(
                            event.getGuildId().get(), // we verified it exists above
                            event.getMessage().getChannelId(),
                            event.getMember().map(Member::getId).orElse(null)
                    );

                    String query = String.join(" ", args);
                    if (queryTooLong(context, query) || context.guildId() == null) return Mono.empty();

                    // Set lastTextChannel so we know where to put now playing messages
                    GuildAudioManager.of(context.guildId()).setLastTextChannel(context.channelId());

                    return Mono.fromCallable(() -> Search.search3(context, query)).subscribeOn(Schedulers.boundedElastic())
                            .flatMap(songs -> songs.stream().findFirst()
                                    .map(firstSong ->
                                            Mono.fromCallable(() -> loadTrack(firstSong, context.guildId()))
                                                    .subscribeOn(Schedulers.boundedElastic()).then()
                                    ).orElse(Mono.empty())
                            );
        }));
    }

    private static Song loadTrack(Song song, Snowflake guildId) {
        GuildAudioManager manager = GuildAudioManager.of(guildId);
        GuildAudioManager.getPlayerManager().loadItem(Stream.getStreamUrl(song), new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                manager.getPlayer().startTrack(track, false);
                log.info("Playing: {} - {}", song.artist(), song.title());
                manager.sendNowPlayingEmbed(song);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {

            }

            @Override
            public void noMatches() {

            }

            @Override
            public void loadFailed(FriendlyException exception) {
                log.error("Loading track failed: ", exception);
            }
        });
        return song;
    }

    private static boolean queryTooLong(MessageCtx ctx, String query) {
        if (query.length() > 1000) {
            log.warn("Blocked oversized query ({} chars) from user {} in guild {} channel {}",
                    query.length(), ctx.memberId().asLong(), ctx.guildId().asLong(), ctx.channelId().asLong());
            return true;
        }

        return false;
    }

    public static void register(String name, Command command) {
        COMMANDS.put(name.toLowerCase(), command);
    }

    public static Command get(String command) {
        return COMMANDS.get(command);
    }

    public boolean isCommand(String content, MessageCreateEvent event) {
        return content.startsWith(Commands.getPrefix(event.getGuildId()))
                || event.getMessage().getUserMentionIds().contains(Bot.getClient().getSelfId());
    }

    public String stripCommandPrefixOrMentions(String content, MessageCreateEvent event) {
        String prefix = getPrefix(event.getGuildId());
        if (content.startsWith(prefix)) {
            return content.substring(prefix.length()).trim();
        }

        // Remove self-mentions from message
        if (event.getMessage().getUserMentionIds().contains(Bot.getClient().getSelfId())) {
            String selfId = Bot.getClient().getSelfId().asString();
            String str1 = "<@" + selfId + ">";
            String str2 = "<@!" + selfId + ">";
            return content.replace(str1, "").replace(str2, "").trim();
        }

        return content; // fallback to original message
    }

    /**
     * Get the command Prefix for the specified (optional) guildId (e.g. !command would be "!")
     * @param guildId optional guildId, as different guilds may have different prefixes
     * @return the prefix to start commands with (e.g. "!")
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static String getPrefix(Optional<Snowflake> guildId) {
        if (guildId.isPresent()) {
            return guildPrefixes.getOrDefault(guildId.get(), DEFAULT_PREFIX);
        }
        return DEFAULT_PREFIX;
    }

    /**
     * Set the prefix for the guildId
     * @param guildId the Snowflake of the Guild we're setting the prefix for
     * @param actionStr the prefix String for the Guild
     */
    public static void setPrefix(Snowflake guildId, String actionStr) {
        guildPrefixes.put(guildId, actionStr);
        log.info("Set {} action char to {}", guildId, actionStr);
        // TODO: In the future, persist to config file or database
    }
}
