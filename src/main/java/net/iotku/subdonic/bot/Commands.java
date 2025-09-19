package net.iotku.subdonic.bot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.PartialMember;
import discord4j.core.spec.VoiceChannelJoinSpec;
import net.iotku.subdonic.subsonic.Song;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
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
                            .doOnError(e -> logger.info("VOICE error: {}", e.getMessage()));
                            }).then());

        register("play", (event, args) -> Mono.justOrEmpty(event.getMember())
                .flatMap(Member::getVoiceState)
                .flatMap(VoiceState::getChannel)
                .flatMap(userChannel
                        -> event.getClient().getSelfMember(event.getGuildId().get()) // TODO: Verify guildId is present
                        .flatMap(PartialMember::getVoiceState)
                        .flatMap(VoiceState::getChannel)
                        .map(botChannel -> botChannel.getId().equals(userChannel.getId()))
                )
                .defaultIfEmpty(false) // either both or member not in same voice channel
                .flatMap(sameChannel -> {
                    if (!sameChannel) {
                        return Objects.requireNonNull(event.getMessage().getChannel().block()).createMessage("Must be in same voice channel to play music!").then();
                    }

                    return Mono.fromCallable(() -> {
                        ObjectMapper mapper = new ObjectMapper();
                        HttpClient client = HttpClient.newHttpClient();
                        String query = String.join(" ", args);

                        String userId = event.getMember()
                                .map(m -> m.getId().asString())
                                .orElse("unknown-user");

                        String guildId = event.getGuildId()
                                .map(Snowflake::asString)
                                .orElse("DM-or-unknown-guild");

                        String channelId = event.getMessage()
                                .getChannelId()
                                .asString();
                        if (query.length() > 1000) {
                            logger.warn("Blocked oversized query ({} chars) from user {} in guild {} channel {}",
                                    query.length(), userId, guildId, channelId);

                            return Mono.empty();
                        }

                        String url = "http://localhost:8080/api/v1/subsonic/search3?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8); // TODO: Sanitation Concerns?
                        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                        HttpResponse<String> response = client.send(req, HttpResponse.BodyHandlers.ofString());

                        List<Song> songs;
                        try {
                            songs = Arrays.asList(mapper.readValue(response.body(), Song[].class));
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }

                        if (songs.isEmpty()) {
                            logger.info("{}: No results found for search {}", guildId, query);
                            return Mono.empty(); // no results
                        }

                        Song song = songs.getFirst();
                        logger.info("Playing: {} - {}", song.artist(), song.title());
                        GuildAudioManager manager = GuildAudioManager.of(event.getGuildId().get()); // TODO: Verify guildId is present
                        String audioURL = "http://localhost:8080/api/v1/subsonic/stream/" + URLEncoder.encode(song.id(), StandardCharsets.UTF_8);
                        GuildAudioManager.getPlayerManager().loadItem(audioURL, new AudioLoadResultHandler() {
                            @Override
                            public void trackLoaded(AudioTrack track) {
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
                        return song;
                    }).then();
        }));
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
