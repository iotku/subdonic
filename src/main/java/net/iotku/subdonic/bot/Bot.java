package net.iotku.subdonic.bot;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
import discord4j.discordjson.json.ApplicationInfoData;
import discord4j.discordjson.possible.Possible;
import discord4j.voice.AudioProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Component
@SuppressWarnings("unused") // SpringBoot loads this via the @Component annotation
public class Bot {
    private final GatewayDiscordClient client;
    private static final Logger logger = LoggerFactory.getLogger(Bot.class);
    private Long ownerId;
    private static final int MAX_RETRY = 5; // Most times we should attempt to repeat a network action
    private static final char ACTION_CHAR = '!'; // TODO: Make this configurable per guild

    private static final Map<String, Command> commands = new HashMap<>();

    static {

        commands.put("ping", event -> Objects.requireNonNull(event.getMessage().getChannel().block()).createMessage("Pong!").then());
        commands.put("pong", event -> Objects.requireNonNull(event.getMessage().getChannel().block()).createMessage("Ping!").then());
    }



    public static final AudioPlayerManager PLAYER_MANAGER;

    static {
        PLAYER_MANAGER = new DefaultAudioPlayerManager();
        // This is an optimization strategy that Discord4J can utilize to minimize allocations
        PLAYER_MANAGER.getConfiguration().setFrameBufferFactory(NonAllocatingAudioFrameBuffer::new);
        AudioSourceManagers.registerRemoteSources(PLAYER_MANAGER);
        AudioSourceManagers.registerLocalSource(PLAYER_MANAGER);
    }

    public Bot(@Value("${discord.token}") String token) {
        // Initialize LavaPlayer
        final AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
        playerManager.getConfiguration()
                .setFrameBufferFactory(NonAllocatingAudioFrameBuffer::new);
        AudioSourceManagers.registerRemoteSources(playerManager);
        final AudioPlayer player = playerManager.createPlayer();


        commands.put("join", event -> Mono.justOrEmpty(event.getMember())
                .flatMap(Member::getVoiceState)
                .flatMap(VoiceState::getChannel)
                .flatMap(channel -> {
                    // Create a player and provider for this session

                    AudioProvider provider = new LavaPlayerAudioProvider(PLAYER_MANAGER.createPlayer());

                    // Join voice channel with our provider
                    return channel.join(spec -> spec.setProvider(provider))
                            .doOnNext(vc -> {
                                logger.info("Joined voice channel {}", vc.getChannelId());

                                // Load and play your FLAC file
                                PLAYER_MANAGER.loadItem("music/test.flac", new AudioLoadResultHandler() {
                                    @Override
                                    public void trackLoaded(AudioTrack track) {
                                        logger.info("Loaded track: {}", track.getInfo().title);
                                        player.playTrack(track);
                                    }

                                    @Override
                                    public void playlistLoaded(AudioPlaylist playlist) {
                                        AudioTrack first = playlist.getTracks().get(0);
                                        logger.info("Loaded playlist, playing first track: {}", first.getInfo().title);
                                        player.playTrack(first);
                                    }

                                    @Override
                                    public void noMatches() {
                                        logger.warn("No matches found for music/test.flac");
                                    }

                                    @Override
                                    public void loadFailed(FriendlyException exception) {
                                        logger.error("Failed to load track", exception);
                                    }
                                });
                            });
                })
                .then());



        // NOTE: Must have "Message Content Intent" enabled in developer dev portal bot settings
        client = DiscordClientBuilder.create(token).build().login().block();
        assert client != null;
        fetchOwnerId();
        run();
    }

    private void run() {

        client.on(ReadyEvent.class).subscribe(event -> logger.info("Discord client is ready"));

        client.getEventDispatcher().on(MessageCreateEvent.class)
                .flatMap(event -> Mono.just(event.getMessage().getContent())
                        .flatMap(content -> Flux.fromIterable(commands.entrySet())
                                .filter(entry -> content.startsWith(ACTION_CHAR + entry.getKey()))
                                .flatMap(entry -> entry.getValue().execute(event))
                                .next()))
                .subscribe();

    }

    private void fetchOwnerId() {
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            this.ownerId = client.rest().getApplicationInfo()
                    .map(ApplicationInfoData::owner)
                    .map(Possible::toOptional)
                    .flatMap(Mono::justOrEmpty)
                    .map(user -> Snowflake.asLong(user.id()))
                    .timeout(Duration.ofSeconds(10))
                    .doOnError(e -> logger.info("Failed to fetch owner ID: {}", e.getMessage()))
                    .block();
            if (this.ownerId != null && this.ownerId != 0) break;
        }

        if (this.ownerId == null || this.ownerId == 0) {
            throw new RuntimeException("Could not determine ownerId");
        }

        logger.info("OwnerId set to: {}", ownerId);
    }

}