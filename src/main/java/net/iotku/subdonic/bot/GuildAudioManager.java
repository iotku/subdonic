package net.iotku.subdonic.bot;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.VoiceStateUpdateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.channel.VoiceChannel;
import discord4j.core.spec.VoiceChannelJoinSpec;
import discord4j.voice.VoiceConnection;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class GuildAudioManager {
    private static final AudioPlayerManager PLAYER_MANAGER;
    private final AudioPlayer player;
    private final AudioTrackScheduler scheduler;
    private final LavaPlayerAudioProvider provider;
    private VoiceConnection voiceConnection;

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
        return MANAGERS.computeIfAbsent(id, ignored -> new GuildAudioManager());
    }

    private GuildAudioManager() {
        player = PLAYER_MANAGER.createPlayer();
        scheduler = new AudioTrackScheduler(player);
        provider = new LavaPlayerAudioProvider(player);

        player.addListener(scheduler);
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