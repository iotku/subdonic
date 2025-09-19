package net.iotku.subdonic.bot;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.VoiceStateUpdateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.channel.*;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.VoiceChannelJoinSpec;
import discord4j.voice.VoiceConnection;
import net.iotku.subdonic.api.v1.dto.Song;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.awt.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class GuildAudioManager {
    private static final AudioPlayerManager PLAYER_MANAGER;
    private final AudioPlayer player;
    private final AudioTrackScheduler scheduler;
    private final LavaPlayerAudioProvider provider;
    private VoiceConnection voiceConnection;
    private final Snowflake guildId;
    private Snowflake lastTextChannel; // store last channel a command came from
    private Snowflake preferredTextChannel; // e.g. a bot-only channel

    static {
        PLAYER_MANAGER = new DefaultAudioPlayerManager();
        // This is an optimization strategy that Discord4J can utilize to minimize allocations
        PLAYER_MANAGER.getConfiguration().setFrameBufferFactory(NonAllocatingAudioFrameBuffer::new);
        AudioSourceManagers.registerRemoteSources(PLAYER_MANAGER);
        AudioSourceManagers.registerLocalSource(PLAYER_MANAGER);
        PLAYER_MANAGER.getConfiguration()
                .setFrameBufferFactory(NonAllocatingAudioFrameBuffer::new);
    }

    private static final Map<Snowflake, GuildAudioManager> MANAGERS = new ConcurrentHashMap<>();

    public static GuildAudioManager of(Snowflake id) {
        return MANAGERS.computeIfAbsent(id, ignored -> new GuildAudioManager(id));
    }

    private GuildAudioManager(Snowflake guildId) {
        player = PLAYER_MANAGER.createPlayer();
        player.setVolume(45);
        scheduler = new AudioTrackScheduler(player);
        provider = new LavaPlayerAudioProvider(player);
        this.guildId = guildId;
        player.addListener(scheduler);
    }

    public EmbedCreateSpec sendNowPlayingEmbed(Song track) {
        String query = track.title() + " " + track.artist();
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String youtubeSearchUrl = "https://www.youtube.com/results?search_query=" + encodedQuery;

        String encodedArtist = URLEncoder.encode(track.artist(), StandardCharsets.UTF_8);
        String youtubeArtistUrl = "https://www.youtube.com/results?search_query=" + encodedArtist;


        EmbedCreateSpec embed = EmbedCreateSpec.builder()
                .title(track.title())
                .url(youtubeSearchUrl)
                .author(track.artist(), youtubeArtistUrl, "https://i.imgur.com/9JhMjVX.png")
//                .description("a description")
//                .thumbnail("https://i.imgur.com/F9BhEoz.png")
//                .image("https://i.imgur.com/F9BhEoz.png")
                .timestamp(Instant.now())
                .footer("Subdonic", "https://i.imgur.com/9JhMjVX.png")
                .build();

        getPreferredTextChannel()
                .flatMap(channel -> channel.createMessage(embed))
                .subscribe();
        return embed;
    }

    public Mono<MessageChannel> getPreferredTextChannel() {
        // preferredTextChannel -> lastTextChannel -> first TextChannel in Guild
        Snowflake fallbackId = preferredTextChannel != null ? preferredTextChannel : lastTextChannel;
        if (fallbackId != null) {
            return Bot.getClient().getChannelById(fallbackId).ofType(MessageChannel.class);
        }

        // fallback to first text channel in guild
        return Bot.getClient().getGuildById(guildId)
                .flatMapMany(Guild::getChannels)
                .ofType(TextChannel.class)
                .next()
                .cast(MessageChannel.class);
    }

//    public Mono<Void> announceTrack(Song track, Snowflake guildId) {
//        GuildAudioManager manager = GuildAudioManager.of(guildId);
//        return Mono.justOrEmpty(manager.getLastTextChannel())
//                .flatMap(client::getChannelById)
//                .cast(MessageChannel.class)
//                .flatMap(ch -> ch.createMessage(nowPlayingEmbed(track)))
//                .then();
//    }

    public void setLastTextChannel(Snowflake lastTextChannel) {
        this.lastTextChannel = lastTextChannel;
    }

    public Mono<VoiceConnection> joinAndTrack(VoiceChannel channel) {
        return channel.join(
                        VoiceChannelJoinSpec.builder()
                                .provider(getProvider())
                                .selfDeaf(true)
                                .build()
                )
                .doOnNext(this::setConnection)
                .flatMap(conn -> { // === idle disconnect logic ===
                    Publisher<Boolean> voiceStateCounter = channel.getVoiceStates()
                            .count()
                            .map(count -> count == 1L); // only the bot left

                    Mono<Void> onDelay = Mono.delay(Duration.ofSeconds(10))
                            .filterWhen(ignored -> voiceStateCounter)
                            .switchIfEmpty(Mono.never())
                            .then();

                    Mono<Void> onEvent = channel.getClient().getEventDispatcher().on(VoiceStateUpdateEvent.class)
                            .filter(vue -> vue.getOld()
                                    .flatMap(VoiceState::getChannelId)
                                    .map(channel.getId()::equals)
                                    .orElse(false))
                            .filterWhen(ignored -> voiceStateCounter)
                            .next()
                            .then();

                    // Disconnect the connection when either fires
                    Mono.firstWithSignal(onDelay, onEvent)
                            .then(voiceConnection.disconnect())
                            .subscribe();

                    return Mono.just(voiceConnection); // immediate result
                });
    }

    // getters
    public AudioPlayer getPlayer() {
        return player;
    }
    public LavaPlayerAudioProvider getProvider() {
        return provider;
    }
    public static AudioPlayerManager getPlayerManager() {
        return PLAYER_MANAGER;
    }

    public Optional<VoiceConnection> getConnection() {
        return Optional.ofNullable(this.voiceConnection);
    }

    // Setters
    public void setConnection(VoiceConnection connection) {
        this.voiceConnection = connection;
    }

}