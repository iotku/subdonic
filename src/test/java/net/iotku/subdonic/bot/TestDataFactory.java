package net.iotku.subdonic.bot;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageCreateEvent;
import net.iotku.subdonic.api.v1.dto.Song;

/**
 * Factory class providing standard test constants and data creation methods.
 * Centralizes test data to ensure consistency across all test cases.
 */
public class TestDataFactory {

    // Standard Test IDs to mock behavior with Discord Snowflakes
    public static final Long TEST_OWNER_ID = 123456789012345678L;
    public static final Long TEST_USER_ID = 987654321098765432L;
    public static final Long TEST_BOT_ID = 555666777888999000L;
    public static final Long TEST_GUILD_ID = 111222333444555666L;
    public static final Long TEST_CHANNEL_ID = 777888999000111222L;
    public static final Long TEST_VOICE_CHANNEL_ID = 333444555666777888L;
    public static final Long TEST_SECOND_USER_ID = 999000111222333444L;
    public static final Long TEST_SECOND_VOICE_CHANNEL_ID = 444555666777888999L;

    // Standard Test Strings
    public static final String DEFAULT_PREFIX = "!";
    public static final String CUSTOM_PREFIX = "?"; // TODO: do we actually set the custom prefix with the bot, we know it CURRENTLY doesn't persist
    public static final String TEST_COMMAND = "play";
    public static final String TEST_QUERY = "test song";
    public static final String TEST_ARTIST = "Test Artist";
    public static final String TEST_TITLE = "Test Song";
    public static final String TEST_ALBUM = "Test Album";

    // Standard Error Messages (matching Commands.java patterns)
    // TODO: We should standardize the erorr messages so we can just get them from prexisting variables
    public static final String VOICE_CHANNEL_REQUIRED = "You must be in a voice channel to use this command!";
    public static final String SAME_CHANNEL_REQUIRED = "You must be in the same voice channel to use this command!";
    public static final String NO_RESULTS_FOUND = "No tracks found for %s";

    /**
     * Creates a standard guild message event for testing.
     *
     * @return A MessageCreateEvent in guild context
     */
    public static MessageCreateEvent createStandardGuildMessage() {
        return new TestEventBuilder()
                .withMessage(DEFAULT_PREFIX + TEST_COMMAND + " " + TEST_QUERY)
                .withUser(TEST_USER_ID)
                .withGuild(TEST_GUILD_ID)
                .withChannel(TEST_CHANNEL_ID)
                .build();
    }

    /**
     * Creates a standard DM message event for testing.
     *
     * @return A MessageCreateEvent in DM context
     */
    public static MessageCreateEvent createStandardDMMessage() {
        return new TestEventBuilder()
                .withMessage(DEFAULT_PREFIX + TEST_COMMAND + " " + TEST_QUERY)
                .withUser(TEST_USER_ID)
                .asDM()
                .withChannel(TEST_CHANNEL_ID)
                .build();
    }

    /**
     * Creates a message event from the bot owner for testing admin functionality.
     *
     * @return A MessageCreateEvent from the owner
     */
    public static MessageCreateEvent createOwnerMessage() {
        return new TestEventBuilder()
                .withMessage(DEFAULT_PREFIX + TEST_COMMAND + " " + TEST_QUERY)
                .withUser(TEST_OWNER_ID)
                .withGuild(TEST_GUILD_ID)
                .withChannel(TEST_CHANNEL_ID)
                .build();
    }

    /**
     * Creates a message event from a user in a voice channel.
     *
     * @return A MessageCreateEvent with user in voice
     */
    public static MessageCreateEvent createVoiceUserMessage() {
        return new TestEventBuilder()
                .withMessage(DEFAULT_PREFIX + TEST_COMMAND + " " + TEST_QUERY)
                .withUser(TEST_USER_ID)
                .withGuild(TEST_GUILD_ID)
                .withChannel(TEST_CHANNEL_ID)
                .withUserInVoice(true)
                .withVoiceChannel(TEST_VOICE_CHANNEL_ID)
                .build();
    }

    /**
     * Creates a message event with bot mention instead of prefix.
     *
     * @return A MessageCreateEvent with bot mention
     */
    public static MessageCreateEvent createBotMentionMessage() { // TODO: also make a message wher ethe mention is not at the beginning
        return new TestEventBuilder()
                .withMessage("<@" + TEST_BOT_ID + "> " + TEST_COMMAND + " " + TEST_QUERY)
                .withUser(TEST_USER_ID)
                .withGuild(TEST_GUILD_ID)
                .withChannel(TEST_CHANNEL_ID)
                .withBotMention()
                .build();
    }

    /**
     * Creates a message event with custom prefix for guild-specific testing.
     *
     * @param prefix The custom prefix to use
     * @return A MessageCreateEvent with custom prefix
     */
    public static MessageCreateEvent createCustomPrefixMessage(String prefix) {
        return new TestEventBuilder()
                .withMessage(prefix + TEST_COMMAND + " " + TEST_QUERY)
                .withUser(TEST_USER_ID)
                .withGuild(TEST_GUILD_ID)
                .withChannel(TEST_CHANNEL_ID)
                .build();
    }

    /**
     * Creates a message event with malformed command for error testing.
     *
     * @return A MessageCreateEvent with malformed content
     */
    public static MessageCreateEvent createMalformedMessage() {
        return new TestEventBuilder()
                .withMessage(DEFAULT_PREFIX + "invalid@#$%^&*()command")
                .withUser(TEST_USER_ID)
                .withGuild(TEST_GUILD_ID)
                .withChannel(TEST_CHANNEL_ID)
                .build();
    }

    /**
     * Creates a message event with no arguments for testing commands without parameters.
     *
     * @param command The command name
     * @return A MessageCreateEvent with no arguments
     */
    public static MessageCreateEvent createNoArgsMessage(String command) {
        return new TestEventBuilder()
                .withMessage(DEFAULT_PREFIX + command)
                .withUser(TEST_USER_ID)
                .withGuild(TEST_GUILD_ID)
                .withChannel(TEST_CHANNEL_ID)
                .build();
    }

    /**
     * Creates a message event with multiple arguments for testing complex commands.
     *
     * @param command The command name
     * @param args The command arguments
     * @return A MessageCreateEvent with multiple arguments
     */
    public static MessageCreateEvent createMultiArgsMessage(String command, String... args) {
        String content = DEFAULT_PREFIX + command + " " + String.join(" ", args);
        return new TestEventBuilder()
                .withMessage(content)
                .withUser(TEST_USER_ID)
                .withGuild(TEST_GUILD_ID)
                .withChannel(TEST_CHANNEL_ID)
                .build();
    }

    /**
     * Creates a test Song object for mocking search results.
     *
     * @return A Song object with test data
     */
    public static Song createTestSong() {
        return new Song(
                TEST_TITLE,
                TEST_ARTIST,
                TEST_ALBUM,
                "2024", // year
                "1" // id
        );
    }

    /**
     * Creates a test Song object with custom parameters.
     *
     * @param id The song ID
     * @param title The song title
     * @param artist The artist name
     * @param album The album name
     * @return A Song object with specified data
     */
    public static Song createTestSong(String id, String title, String artist, String album) {
        return new Song(
                title,
                artist,
                album,
                "2024", // year
                id
        );
    }

    /**
     * Creates multiple test songs for pagination testing.
     *
     * @param count The number of songs to create
     * @return An array of Song objects
     */
    public static Song[] createTestSongs(int count) {
        Song[] songs = new Song[count];
        for (int i = 0; i < count; i++) {
            songs[i] = createTestSong(
                    String.valueOf(i + 1),
                    TEST_TITLE + " " + (i + 1),
                    TEST_ARTIST + " " + (i + 1),
                    TEST_ALBUM + " " + (i + 1)
            );
        }
        return songs;
    }

    /**
     * Converts a Long ID to a Snowflake for Discord4J compatibility.
     *
     * @param id The Long ID
     * @return A Snowflake object
     */
    public static Snowflake toSnowflake(Long id) {
        return Snowflake.of(id);
    }

    /**
     * Creates a formatted command string with prefix.
     *
     * @param command The command name
     * @param args Optional arguments
     * @return A formatted command string
     */
    public static String formatCommand(String command, String... args) {
        StringBuilder sb = new StringBuilder(DEFAULT_PREFIX).append(command);
        for (String arg : args) {
            sb.append(" ").append(arg);
        }
        return sb.toString();
    }

    /**
     * Creates a formatted bot mention command string.
     *
     * @param command The command name
     * @param args Optional arguments
     * @return A formatted bot mention command string
     */
    public static String formatBotMentionCommand(String command, String... args) {
        StringBuilder sb = new StringBuilder("<@").append(TEST_BOT_ID).append("> ").append(command);
        for (String arg : args) {
            sb.append(" ").append(arg);
        }
        return sb.toString();
    }
}