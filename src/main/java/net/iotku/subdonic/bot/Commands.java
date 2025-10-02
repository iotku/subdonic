package net.iotku.subdonic.bot;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.PartialMember;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.voice.VoiceConnection;

import java.io.IOException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.iotku.subdonic.api.v1.dto.Song;
import net.iotku.subdonic.client.Search;
import static net.iotku.subdonic.client.Stream.getStreamUrl;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
        register("join", Commands::join);
        register("disconnect", Commands::disconnect);

        // Play
        register("p", Commands::play);
        register("play", Commands::play);
        register("add", Commands::play);

        // Stop
        register("stop", Commands::stop);
        register("pause", Commands::stop);

        // Skip
        register("skip", Commands::skip);
        register("s", Commands::skip);

        // Random
        register("r", Commands::random);
        register("rand", Commands::random);
        register("random", Commands::random);

        // View Queue
        register("list", Commands::list);
        register("queue", Commands::list);

        // Search
        register("search", Commands::search);
    }

    private static Mono<Void> stop(MessageCreateEvent event, String[] strings) {
        MessageCtx ctx = MessageCtx.buildCtx(event);
        if (ctx.guildId() == null) return Mono.empty(); // do nothing in DMs

        return ensureSameChannelOrJoin(event).flatMap(sameChannel -> {
            if (!sameChannel) return Mono.empty();
            GuildAudioManager manager = GuildAudioManager.of(ctx.guildId());
            manager.getPlayer().setPaused(true);
            return Mono.empty();
        });
    }

    private static Mono<Void> skip (MessageCreateEvent event, String[] args) {
        MessageCtx ctx = MessageCtx.buildCtx(event);
        if (ctx.guildId() == null) return Mono.empty(); // Do nothing in DMs
        return ensureSameChannelOrJoin(event).flatMap(sameChannel -> {
            if (!sameChannel) return Mono.empty(); // must be in same channel

            GuildAudioManager manager = GuildAudioManager.of(ctx.guildId());
            // Set lastTextChannel so we know where to put now playing messages
            manager.setLastTextChannel(ctx.channelId());
            int skipAmt;
            if (args.length == 0) {
                manager.getScheduler().skip(1);
            } else if (args.length == 1) {
                try {
                    skipAmt = Integer.parseInt(args[0]);
                    manager.getScheduler().skip(skipAmt);
                } catch (NumberFormatException nfe) {
                    // just do nothing, invalid number
                }
            }

            return Mono.empty();
        });
    }

    private static Mono<Void> join(MessageCreateEvent event, String[] args) {
        return Mono.justOrEmpty(event.getMember())
                .flatMap(Member::getVoiceState)
                .flatMap(VoiceState::getChannel)
                .flatMap(channel -> {
                    GuildAudioManager manager = GuildAudioManager.of(channel.getGuildId());
                    // Set lastTextChannel so we know where to put now playing messages
                    manager.setLastTextChannel(event.getMessage().getChannelId());
                    return manager.joinAndTrack(channel).then();
                });
    }

    private static Mono<Void> disconnect(MessageCreateEvent event, String[] args) {
        MessageCtx ctx = MessageCtx.buildCtx(event);
        if (ctx.guildId() == null) return Mono.empty(); // Do nothing in DMs

        return ensureSameChannelOrJoin(event).flatMap(sameChannel -> {
            if (!sameChannel) return Mono.empty(); // must be in same channel
            GuildAudioManager manager = GuildAudioManager.of(ctx.guildId());

            manager.setLastTextChannel(ctx.channelId());
            return Mono.justOrEmpty(manager.getConnection())
                    .flatMap(VoiceConnection::disconnect)
                    .then(event.getMessage()
                            .getChannel()
                            .flatMap(ch -> ch.createMessage("Disconnected from voice channel.")))
                    .then();
        });
    }

    private static Mono<Void> play (MessageCreateEvent event, String[] args) {
        MessageCtx ctx = MessageCtx.buildCtx(event);
        if (ctx.guildId() == null) return Mono.empty(); // do nothing in DMs

        return ensureSameChannelOrJoin(event).flatMap(sameChannel -> {
            if (!sameChannel) return Mono.empty(); // must be in same channel

            if (args.length == 0) {
                // just resume if paused
                if (GuildAudioManager.of(ctx.guildId()).getPlayer().isPaused()) {
                    GuildAudioManager.of(ctx.guildId()).getPlayer().setPaused(false);
                }
                return Mono.empty();
            }

            // Check for searched song
            // Since we start our search number at 1, 0 is considered an empty value
            int searchNum = 0;
            if (args.length == 1) {
                try {
                    searchNum = Integer.parseInt(args[0]);
                } catch (NumberFormatException nfe) {
                    // just do nothing, invalid number
                }
            }

            // We're trying to play a search result
            HashMap<Integer, Song> lastSearchResults = GuildAudioManager.of(ctx.guildId()).getLastSearchResults();
            if (searchNum > 0 && lastSearchResults.containsKey(searchNum)) {
                // get song by id
                final int finalSearchNum = searchNum;
                return Mono.fromCallable(() -> loadTrack(lastSearchResults.get(finalSearchNum), ctx.guildId()))
                        .subscribeOn(Schedulers.boundedElastic())
                        .then();
            } else if (searchNum > 0) {
                log.info("Search number {} not found in last search results, continuing with normal query", searchNum);
            }

            // Otherwise just preform normal query
            String query = String.join(" ", args);
            if (queryTooLong(ctx, query)) return Mono.empty();

            // Set lastTextChannel so we know where to put now playing messages
            GuildAudioManager.of(ctx.guildId()).setLastTextChannel(ctx.channelId());

            return Mono.fromCallable(() -> Search.search3(ctx, query)).subscribeOn(Schedulers.boundedElastic())
                    .flatMap(songs -> songs.stream().findFirst()
                            .map(firstSong ->
                                    Mono.fromCallable(() -> loadTrack(firstSong, ctx.guildId()))
                                            .subscribeOn(Schedulers.boundedElastic())
                                            .then()
                            )
                            .orElseGet(() -> event.getMessage().getChannel()
                                    .flatMap(ch -> ch.createMessage("No tracks found for " + query)
                                            .then()))
                    );
        });
    }

    private static Mono<Void> random (MessageCreateEvent event, String[] args) {
        MessageCtx context = MessageCtx.buildCtx(event);
        if (context.guildId() == null) return Mono.empty(); // do nothing in DMs
        // TODO: In the future, consider adding a cooldown to avoid abuse

        return ensureSameChannelOrJoin(event).flatMap(sameChannel -> {
            if (!sameChannel) return Mono.empty(); // Must be in the same voice channel as the bot

            int count = 1;
            if (args.length == 1) {
                try {
                    count = Integer.parseInt(args[0]);
                } catch (NumberFormatException ignored) {} // just do nothing, invalid number
            }

            // Avoid pointless !rand 0 or potential underflow
            if (count < 1) {
                return Mono.empty();
            }

            // Maximum count limit per command
            if (count > 5) {
                count = 5;
            }

            // Set lastTextChannel so we know where to put now playing messages
            GuildAudioManager.of(context.guildId()).setLastTextChannel(context.channelId());

            int finalCount = count;
            return Mono.fromCallable(() -> Search.random(context, 10)) // we request 10 results, but limit to count
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMapMany(songs -> {
                        Stream<Song> stream = songs.stream();
                        stream = stream.limit(finalCount);
                        return Flux.fromStream(stream)
                                .flatMap(song ->
                                        Mono.fromCallable(() -> loadTrack(song, context.guildId()))
                                                .subscribeOn(Schedulers.boundedElastic())
                                                .then()
                                );
                    })
                    .then();
        });
    }

    private static Mono<Void> search (MessageCreateEvent event, String[] args) throws IOException, InterruptedException { // TODO: SHOULD we set last text channel for this?
        MessageCtx ctx = MessageCtx.buildCtx(event);
        if (ctx.guildId() == null) return Mono.empty(); // do nothing in DMs

        String query = String.join(" ", args);
        if (queryTooLong(ctx, query)) return Mono.empty();

        // Generate search results
        List<Song> results = Search.search3(ctx, query);

        if (results.isEmpty()) {
            EmbedCreateSpec noResults = EmbedCreateSpec.builder()
                    .title("Search results for: " + query)
                    .description("No results found.")
                    .build();

            return event.getMessage().getChannel()
                    .flatMap(ch -> ch.createMessage(MessageCreateSpec.builder()
                            .addEmbed(noResults)
                            .build()))
                    .then();
        }

        // Limit to 5 Pages
        List<List<Song>> pages = new ArrayList<>();
        for (int i = 0; i < results.size(); i += 5) {
            pages.add(results.subList(i, Math.min(i + 5, results.size())));
        }

        HashMap<Integer, Song> lastSearchResults = GuildAudioManager.of(ctx.guildId()).getLastSearchResults();
        lastSearchResults.clear();
        AtomicInteger counter = new AtomicInteger(1);

        List<EmbedCreateSpec> embeds = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            List<Song> page = pages.get(i);
            String desc = page.stream()
                    .map(s -> {
                        int num = counter.getAndIncrement();   // grab & increment
                        lastSearchResults.put(num, s);    // save mapping
                        return num + ". " + s.artist() + " - " + s.title() + " (" + s.album() + ")";
                    })
                    .collect(Collectors.joining("\n"));

            embeds.add(EmbedCreateSpec.builder()
                    .title("Search results for: " + query)
                    .description(desc)
                    .footer("Page " + (i + 1) + " of " + pages.size(), null)
                    .build());
        }

        AtomicInteger currentPage = new AtomicInteger(0);

        // Send first page with buttons
        return event.getMessage().getChannel()
                .flatMap(channel -> channel.createMessage(MessageCreateSpec.builder()
                        .addEmbed(embeds.getFirst())
                        .addComponent(ActionRow.of(
                                Button.secondary("search_prev", "Prev").disabled(true),
                                Button.secondary("search_next", "Next").disabled(embeds.size() <= 1)
                        ))
                        .build()))
                .flatMap(message ->
                        message.getClient().on(ButtonInteractionEvent.class)
                                .filter(e -> e.getMessageId().equals(message.getId()))
                                .filter(e -> event.getMessage().getAuthor()
                                        .map(user -> user.getId().equals(e.getUser().getId()))
                                        .orElse(false))
                                .flatMap(e -> {
                                    int page = currentPage.get();
                                    if (e.getCustomId().equals("search_prev") && page > 0) {
                                        page--;
                                        currentPage.set(page);
                                    } else if (e.getCustomId().equals("search_next") && page < embeds.size() - 1) {
                                        page++;
                                        currentPage.set(page);
                                    } else {
                                        return e.reply().withEphemeral(true).withContent("No more pages.");
                                    }

                                    Button prevButton = Button.secondary("search_prev", "Prev").disabled(page == 0);
                                    Button nextButton = Button.secondary("search_next", "Next").disabled(page == embeds.size() - 1);

                                    return e.edit()
                                            .withEmbeds(embeds.get(page))
                                            .withComponents(ActionRow.of(prevButton, nextButton));
                                })
                                .then()
                );
    }

    private static Mono<Void> list (MessageCreateEvent event, String[] args) { // TODO: do we set last text channel for this?
        MessageCtx ctx = MessageCtx.buildCtx(event);
        if (ctx.guildId() == null) return Mono.empty(); // do nothing in DMs

        GuildAudioManager manager = GuildAudioManager.of(ctx.guildId());

        // Generate search results
        Queue<AudioTrack> queue = manager.getScheduler().getQueue();
        int count = 0;
        List<Song> results = new ArrayList<>();
        for (AudioTrack item : queue) {
            results.add((Song) item.getUserData());
            count++;
            if (count >= 20) break;
        }

        if (results.isEmpty()) {
            EmbedCreateSpec noResults = EmbedCreateSpec.builder()
                    .title("Queue is empty")
                    .description("Use !search and !play to find some tracks!")
                    .build();

            return event.getMessage().getChannel()
                    .flatMap(ch -> ch.createMessage(MessageCreateSpec.builder()
                            .addEmbed(noResults)
                            .build()))
                    .then();
        }

        // Limit to 5 Pages
        List<List<Song>> pages = new ArrayList<>();
        for (int i = 0; i < results.size(); i += 5) {
            pages.add(results.subList(i, Math.min(i + 5, results.size())));
        }

        HashMap<Integer, Song> lastSearchResults = manager.getLastSearchResults();
        lastSearchResults.clear();
        AtomicInteger counter = new AtomicInteger(1);

        List<EmbedCreateSpec> embeds = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            List<Song> page = pages.get(i);
            String desc = page.stream()
                    .map(s -> {
                        int num = counter.getAndIncrement();   // grab & increment
                        lastSearchResults.put(num, s);    // save mapping
                        return num + ". " + s.artist() + " - " + s.title() + " (" + s.album() + ")";
                    })
                    .collect(Collectors.joining("\n"));

            embeds.add(EmbedCreateSpec.builder()
                    .title("Playback Queue")
                    .description(desc)
                    .footer("Page " + (i + 1) + " of " + pages.size(), null)
                    .build());
        }

        AtomicInteger currentPage = new AtomicInteger(0);

        // Send first page with buttons
        return event.getMessage().getChannel()
                .flatMap(channel -> channel.createMessage(MessageCreateSpec.builder()
                        .addEmbed(embeds.getFirst())
                        .addComponent(ActionRow.of(
                                Button.secondary("search_prev", "Prev").disabled(true),
                                Button.secondary("search_next", "Next").disabled(embeds.size() <= 1)
                        ))
                        .build()))
                .flatMap(message ->
                        message.getClient().on(ButtonInteractionEvent.class)
                                .filter(e -> e.getMessageId().equals(message.getId()))
                                .filter(e -> event.getMessage().getAuthor()
                                        .map(user -> user.getId().equals(e.getUser().getId()))
                                        .orElse(false))
                                .flatMap(e -> {
                                    int page = currentPage.get();
                                    if (e.getCustomId().equals("search_prev") && page > 0) {
                                        page--;
                                        currentPage.set(page);
                                    } else if (e.getCustomId().equals("search_next") && page < embeds.size() - 1) {
                                        page++;
                                        currentPage.set(page);
                                    } else {
                                        return e.reply().withEphemeral(true).withContent("No more pages.");
                                    }

                                    Button prevButton = Button.secondary("search_prev", "Prev").disabled(page == 0);
                                    Button nextButton = Button.secondary("search_next", "Next").disabled(page == embeds.size() - 1);

                                    return e.edit()
                                            .withEmbeds(embeds.get(page))
                                            .withComponents(ActionRow.of(prevButton, nextButton));
                                })
                                .then()
                );
    }

    private static Mono<Boolean> ensureSameChannelOrJoin(MessageCreateEvent event) {
        return Mono.justOrEmpty(event.getMember())
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

                                    log.info("{} not in same voice voice channel, don't run command.",
                                            event.getMember().map(m -> m.getId().asString()).orElse("Unknown user"));
                                    return event.getMessage().getChannel()
                                            .flatMap(ch -> ch.createMessage("You must be in the same voice channel to use this command!"))
                                            .then(Mono.just(false));
                                })
                                // bot not in any channel, Join User's channel
                                .switchIfEmpty(GuildAudioManager.of(guildId).joinAndTrack(userChannel).thenReturn(true))
                        )
                ).switchIfEmpty(Mono.defer(() -> { // user is not in a voice channel
                    log.info("{} not in a voice channel, don't run command.",
                            event.getMember().map(m -> m.getId().asString()).orElse("Unknown user"));
                    return event.getMessage().getChannel()
                            .flatMap(ch -> ch.createMessage("You must be in a voice channel to use this command!"))
                            .then(Mono.just(false));
                }));
    }

    private static Song loadTrack(Song song, Snowflake guildId) {
        GuildAudioManager manager = GuildAudioManager.of(guildId);
        GuildAudioManager.getPlayerManager().loadItem(getStreamUrl(song), new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                track.setUserData(song);
                manager.getScheduler().play(track);
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

    public boolean isCommand(MessageCreateEvent event) {
        return event.getMessage().getContent().startsWith(Commands.getPrefix(event.getGuildId()))
                || event.getMessage().getUserMentionIds().contains(Bot.getClient().getSelfId());
    }

    /**
     * Remove command prefix or bot mentions from the provided message
     * @param event MessageCreateEvent with message to strip out prefix/mentions
     * @return a String of the message after striping out the prefix/mentions
     */
    public String stripCommandPrefixOrMentions(MessageCreateEvent event) {
        String prefix = getPrefix(event.getGuildId());
        String content = event.getMessage().getContent();
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
