package net.iotku.subdonic.bot;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
import discord4j.discordjson.json.ApplicationInfoData;
import discord4j.discordjson.possible.Possible;
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

import static net.iotku.subdonic.bot.GuildAudioManager.PLAYER_MANAGER;

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

    public Bot(@Value("${discord.token}") String token) {

//        AudioTrackScheduler scheduler = new AudioTrackScheduler(player);


        commands.put("join", event -> Mono.justOrEmpty(event.getMember())
                .flatMap(Member::getVoiceState)
                .flatMap(VoiceState::getChannel)
                .flatMap(channel -> {
                    GuildAudioManager manager = GuildAudioManager.of(channel.getGuildId());

//                    // Create a player and provider for this session
//                    final AudioPlayer player = PLAYER_MANAGER.createPlayer();
//                    AudioProvider provider = new LavaPlayerAudioProvider(PLAYER_MANAGER.createPlayer());

                    // Join voice channel with our provider
                    return channel.join(spec -> spec.setProvider(manager.getProvider()))
                            .doOnNext(vc -> {
                                logger.info("Joined voice channel {}", vc.getChannelId());

                                // Load and play your FLAC file
                                PLAYER_MANAGER.loadItem("music/sample-12s.mp3", new AudioLoadResultHandler() {
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