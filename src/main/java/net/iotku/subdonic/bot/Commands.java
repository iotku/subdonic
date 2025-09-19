package net.iotku.subdonic.bot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.VoiceStateUpdateEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.PartialMember;
import discord4j.core.spec.VoiceChannelJoinSpec;
import discord4j.voice.VoiceConnection;
import net.iotku.subdonic.subsonic.Song;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Commands {
    private static final String DEFAULT_ACTION_STR = "!";
    private static final Map<Snowflake, String> guildActionStrs = new ConcurrentHashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(Commands.class);
    static HttpClient httpClient = HttpClient.newHttpClient();
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
                    Mono<VoiceConnection> joinMono =  channel.join(VoiceChannelJoinSpec.builder().provider(manager.getProvider()).selfDeaf(true).build());
                    return joinMono.flatMap(connection -> {
                        manager.setConnection(connection);

                        Publisher<Boolean> voiceStateCounter = channel.getVoiceStates()
                                .count()
                                .map(count -> count == 1L);

                        Mono<Void> onDelay = Mono.delay(Duration.ofSeconds(10))
                                .filterWhen(ignored -> voiceStateCounter)
                                .switchIfEmpty(Mono.never())
                                .then();

                        Mono<Void> onEvent = channel.getClient().getEventDispatcher().on(VoiceStateUpdateEvent.class)
                                .filter(vue -> vue.getOld().flatMap(VoiceState::getChannelId).map(channel.getId()::equals).orElse(false))
                                .filterWhen(ignored -> voiceStateCounter)
                                .next()
                                .then();

                        return Mono.firstWithSignal(onDelay, onEvent).then(connection.disconnect());
                    });
                }));

        register("disconnect", (event, args) -> {
            Snowflake guildId = event.getGuildId().orElse(null);
            if (guildId == null) return Mono.empty(); // DMs

            GuildAudioManager manager = GuildAudioManager.of(guildId);

            return manager.getVoiceConnection().disconnect()
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
                        .map(botChannel -> botChannel.getId().equals(userChannel.getId()))
                        )).defaultIfEmpty(false) // either both or member not in same voice channel
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

                    return Mono.fromCallable(() -> search3(context, query)).subscribeOn(Schedulers.boundedElastic())
                            .flatMap(songs -> songs.stream().findFirst()
                                    .map(firstSong ->
                                            Mono.fromCallable(() -> loadTrack(firstSong, context.guildId()))
                                                    .subscribeOn(Schedulers.boundedElastic()).then()
                                    ).orElse(Mono.empty())
                            );
        }));
    }

    private static Song loadTrack(Song song, Snowflake guildId) {
        String audioURL = "http://localhost:8080/api/v1/subsonic/stream/" + URLEncoder.encode(song.id(), StandardCharsets.UTF_8);
        GuildAudioManager manager = GuildAudioManager.of(guildId);
        GuildAudioManager.getPlayerManager().loadItem(audioURL, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                manager.getPlayer().startTrack(track, false);
                logger.info("Playing: {} - {}", song.artist(), song.title());
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
    }

    private static boolean queryTooLong(MessageCtx ctx, String query) {
        if (query.length() > 1000) {
            logger.warn("Blocked oversized query ({} chars) from user {} in guild {} channel {}",
                    query.length(), ctx.memberId().asLong(), ctx.guildId().asLong(), ctx.channelId().asLong());
            return true;
        }

        return false;
    }

    public static List<Song> search3(MessageCtx ctx, String query) throws IOException, InterruptedException {
        ObjectMapper mapper = new ObjectMapper();
        HttpResponse<String> response;

        String url = "http://localhost:8080/api/v1/subsonic/search3?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            logger.warn("Subsonic search failed: {}", response.body());
            return Collections.emptyList();
        }

        try {
            logger.info("({}:{}) {}: No results found for search {}", ctx.guildId().asLong(), ctx.channelId().asLong(), ctx.memberId().asLong(), query);
            return Arrays.asList(mapper.readValue(response.body(), Song[].class));
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse JSON from Subsonic API", e);
            return Collections.emptyList();
        }
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
