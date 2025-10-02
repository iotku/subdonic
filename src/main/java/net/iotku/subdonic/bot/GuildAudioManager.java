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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class GuildAudioManager {
    private static final AudioPlayerManager PLAYER_MANAGER;
    private final AudioPlayer player;
    private final AudioTrackScheduler scheduler;
    private final LavaPlayerAudioProvider provider;
    private VoiceConnection voiceConnection;
    private final Snowflake guildId;
    private Snowflake lastTextChannel; // store last channel a command came from
    private Snowflake preferredTextChannel; // e.g. a bot-only channel
    private final HashMap<Integer, Song> lastSearchResults = new HashMap<>();
    private static final Logger log = LoggerFactory.getLogger(GuildAudioManager.class);

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

        // Build scheduler
        scheduler = new AudioTrackScheduler(player);
        // Attach the scheduler to the player
        player.addListener(scheduler);
        // Attach the consumer that runs when playback starts
        scheduler.setOnTrackStart(track -> {
            // This code runs whenever a track starts playing
            Song song = (Song) track.getUserData(); // attached metadata
            sendNowPlayingEmbed(song);
        });

        scheduler.setOnTrackAdd(track -> {
            Song song = (Song) track.getUserData(); // attached metadata
            getPreferredTextChannel().flatMap(ch -> ch.createMessage("Added: " + song.artist() + " - " + song.title()))
                    .subscribe();
        });

        provider = new LavaPlayerAudioProvider(player);

        this.guildId = guildId;
    }

    /**
     * Send a nowPlaying enbed to the most preferred text channel
     * @param track a Song with the metadata to be sent in the embed message
     * @return a EmbedCreateSpec, we mostly just care about the side effect of sending the message
     */
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
                .footer(getScheduler().getQueue().size() + " Tracks Queued.", "https://i.imgur.com/9JhMjVX.png")
                .build();

        getPreferredTextChannel()
                .flatMap(channel -> channel.createMessage(embed))
                .subscribe();
        return embed;
    }

    /**
     * Return the channel the message should most likely be sent to for good UX.
     * Send to preferredTextChannel -> lastTextChannel -> first TextChannel in Guild
     * @return a {@code Mono} that completes with the preferred MessageChannel
     */
    public Mono<MessageChannel> getPreferredTextChannel() {
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

    /**
     * Track the last channel that a text message was sent to.
     * @param lastTextChannel the last place the bot received a textMessage
     */
    public void setLastTextChannel(Snowflake lastTextChannel) {
        this.lastTextChannel = lastTextChannel;
    }


    private Mono<Boolean> isAlone(VoiceChannel channel) {
        return channel.getVoiceStates()
                .count()
                .map(count -> count == 1L);
    }

    /**
     * Join a VoiceChannel and keep track of membership, e.g. disconnect when empty.
     * @param channel a VoiceChannel to join and keep track membership
     * @return a {@code Mono} that completes with the active {@link VoiceConnection}
     *         once the bot has joined
     */
    public Mono<VoiceConnection> joinAndTrack(VoiceChannel channel) {
        return channel.join(
                        VoiceChannelJoinSpec.builder()
                                .provider(getProvider())
                                .selfDeaf(true)
                                .build()
                )
                .doOnNext(this::setConnection)
                .flatMap(conn -> {
                    AtomicReference<Disposable> listenerRef = new AtomicReference<>();
                    AtomicReference<Disposable> pendingDisconnect = new AtomicReference<>();

                    Disposable listener = channel.getClient().getEventDispatcher()
                            .on(VoiceStateUpdateEvent.class)
                            .filter(vue -> {
                                var oldChannel = vue.getOld().flatMap(VoiceState::getChannelId);
                                var newChannel = vue.getCurrent().getChannelId();
                                // Only consider events where the user changed channels
                                return !Objects.equals(oldChannel.orElse(null), newChannel.orElse(null))
                                        && (oldChannel.map(channel.getId()::equals).orElse(false)
                                        || newChannel.map(channel.getId()::equals).orElse(false));
                            })
                            .flatMap(vue -> {
                                // Bot left the channel
                                if (vue.getCurrent().getUserId().equals(Bot.getClient().getSelfId())
                                        && !vue.getCurrent().getChannelId().map(channel.getId()::equals).orElse(false)) {
                                    // Dispose any pending timer
                                    Disposable timer = pendingDisconnect.getAndSet(null);
                                    if (timer != null && !timer.isDisposed()) timer.dispose();

                                    // Dispose the listener itself
                                    Disposable l = listenerRef.getAndSet(null);
                                    if (l != null && !l.isDisposed()) l.dispose();

                                    log.info("Bot left channel, cleaned up listener and timers");
                                    return Mono.empty();
                                }
                                return isAlone(channel);
                            })
                            .subscribe(alone -> {
                                if (alone) {
                                    if (pendingDisconnect.get() == null || pendingDisconnect.get().isDisposed()) {
                                        log.info("Channel empty, disconnecting in 10s");
                                        Disposable timer = Mono.delay(Duration.ofSeconds(10))
                                                .flatMap(t -> isAlone(channel))
                                                .filter(Boolean::booleanValue)
                                                .flatMap(t -> conn.disconnect())
                                                .subscribe();
                                        pendingDisconnect.set(timer);
                                    }
                                } else {
                                    Disposable timer = pendingDisconnect.getAndSet(null);
                                    if (timer != null && !timer.isDisposed()) timer.dispose();
                                    log.info("Channel refilled, disposing timer");
                                }
                            });

                    listenerRef.set(listener); // store it so we can dispose inside lambda

                    return Mono.just(conn);
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

    public AudioTrackScheduler getScheduler() {
        return scheduler;
    }

    public Optional<VoiceConnection> getConnection() {
        return Optional.ofNullable(this.voiceConnection);
    }

    public HashMap<Integer, Song> getLastSearchResults() {
        return lastSearchResults;
    }

    // Setters
    public void setConnection(VoiceConnection connection) {
        this.voiceConnection = connection;
    }

}