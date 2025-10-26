package net.iotku.subdonic.bot;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test suite for command parsing functionality.
 * Tests prefix detection, bot mention detection, and argument parsing.
 * 
 * Requirements tested:
 * - 1.1: Command identification with configured prefix
 * - 1.2: Command identification with bot mentions
 * - 1.3: Command argument separation and parsing
 * - 1.4: Invalid command handling
 * - 1.5: Custom guild prefix support
 * - 7.5: Malformed command handling
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
@DisplayName("Command Parsing Tests")
public class CommandParsingTests {

    private Commands commands;
    private GatewayDiscordClient mockClient;

    @BeforeEach
    void setUp() {
        // Create a simple mock client for Bot.getClient() calls
        mockClient = mock(GatewayDiscordClient.class);
        when(mockClient.getSelfId()).thenReturn(Snowflake.of(TestDataFactory.TEST_BOT_ID));

        // Create a mock Bot instance to pass to Commands
        Bot mockBot = mock(Bot.class);
        when(mockBot.getOwnerId()).thenReturn(TestDataFactory.TEST_OWNER_ID);

        commands = new Commands(mockBot);
    }

    @AfterEach
    void tearDown() {
        // Clean up any custom prefixes set during tests to ensure test isolation
        // This prevents tests from affecting each other when run as a suite
        Snowflake guildId = Snowflake.of(TestDataFactory.TEST_GUILD_ID);
        Commands.setPrefix(guildId, TestDataFactory.DEFAULT_PREFIX);
    }

    private MessageCreateEvent createSimpleMessageEvent(String content, Long guildId) {
        MessageCreateEvent event = mock(MessageCreateEvent.class);
        Message message = mock(Message.class);

        when(message.getContent()).thenReturn(content);
        when(event.getMessage()).thenReturn(message);

        if (guildId != null) {
            when(event.getGuildId()).thenReturn(Optional.of(Snowflake.of(guildId)));
        } else {
            when(event.getGuildId()).thenReturn(Optional.empty());
        }

        // Default to no mentions
        when(message.getUserMentionIds()).thenReturn(List.of());

        return event;
    }

    private MessageCreateEvent createMessageEventWithMentions(String content, Long guildId, List<Long> mentionIds) {
        MessageCreateEvent event = createSimpleMessageEvent(content, guildId);
        Message message = event.getMessage();

        List<Snowflake> mentions = mentionIds.stream().map(Snowflake::of).toList();
        when(message.getUserMentionIds()).thenReturn(mentions);

        return event;
    }

    // ===== PREFIX DETECTION TESTS =====

    @Test
    @DisplayName("Should detect command with default prefix")
    void testDefaultPrefixCommandDetection() {
        // Requirement 1.1: WHEN a message starts with the configured prefix THEN the
        // system SHALL identify it as a command
        try (MockedStatic<Bot> mockedBot = mockStatic(Bot.class)) {
            mockedBot.when(Bot::getClient).thenReturn(mockClient);

            MessageCreateEvent event = createSimpleMessageEvent("!play test song", TestDataFactory.TEST_GUILD_ID);
            assertTrue(commands.isCommand(event), "Should detect command with default prefix '!'");
        }
    }

    @Test
    @DisplayName("Should not detect non-command message with default prefix")
    void testNonCommandWithDefaultPrefix() {
        try (MockedStatic<Bot> mockedBot = mockStatic(Bot.class)) {
            mockedBot.when(Bot::getClient).thenReturn(mockClient);

            MessageCreateEvent event = createSimpleMessageEvent("Hello world!", TestDataFactory.TEST_GUILD_ID);
            assertFalse(commands.isCommand(event), "Should not detect regular message as command");
        }
    }

    @Test
    @DisplayName("Should detect command with custom guild prefix")
    void testCustomGuildPrefixCommandDetection() {
        // Requirement 1.5: WHEN command prefix is customized per guild THEN the system
        // SHALL use the correct prefix for command detection
        try (MockedStatic<Bot> mockedBot = mockStatic(Bot.class)) {
            mockedBot.when(Bot::getClient).thenReturn(mockClient);

            Snowflake guildId = Snowflake.of(TestDataFactory.TEST_GUILD_ID);
            String customPrefix = "?";

            // Set custom prefix for guild
            Commands.setPrefix(guildId, customPrefix);

            MessageCreateEvent event = createSimpleMessageEvent("?play test song", TestDataFactory.TEST_GUILD_ID);

            assertTrue(commands.isCommand(event), "Should detect command with custom guild prefix");

            // Verify the custom prefix is returned
            assertEquals(customPrefix, Commands.getPrefix(event.getGuildId()),
                    "Should return custom prefix for guild");
        }
    }

    @Test
    @DisplayName("Should use default prefix for guild without custom prefix")
    void testDefaultPrefixForGuildWithoutCustom() {
        MessageCreateEvent event = createSimpleMessageEvent("!play test", TestDataFactory.TEST_GUILD_ID);

        assertEquals(TestDataFactory.DEFAULT_PREFIX, Commands.getPrefix(event.getGuildId()),
                "Should return default prefix for guild without custom prefix");
    }

    @Test
    @DisplayName("Should use default prefix for DM messages")
    void testDefaultPrefixForDMMessages() {
        MessageCreateEvent event = createSimpleMessageEvent("!play test", null);

        assertEquals(TestDataFactory.DEFAULT_PREFIX, Commands.getPrefix(event.getGuildId()),
                "Should return default prefix for DM messages");
    }

    // ===== BOT MENTION DETECTION TESTS =====

    @Test
    @DisplayName("Should detect command with bot mention")
    void testBotMentionCommandDetection() {
        // Requirement 1.2: WHEN a message mentions the bot THEN the system SHALL
        // identify it as a command
        try (MockedStatic<Bot> mockedBot = mockStatic(Bot.class)) {
            mockedBot.when(Bot::getClient).thenReturn(mockClient);

            MessageCreateEvent event = createMessageEventWithMentions(
                    "<@" + TestDataFactory.TEST_BOT_ID + "> play test song",
                    TestDataFactory.TEST_GUILD_ID,
                    List.of(TestDataFactory.TEST_BOT_ID));

            assertTrue(commands.isCommand(event), "Should detect command with bot mention");
        }
    }

    @Test
    @DisplayName("Should detect command with bot mention using nickname format")
    void testBotMentionNicknameFormat() {
        try (MockedStatic<Bot> mockedBot = mockStatic(Bot.class)) {
            mockedBot.when(Bot::getClient).thenReturn(mockClient);
            // NOTE: <@! is a 'nickname' mention, legacy format
            MessageCreateEvent event = createMessageEventWithMentions(
                    "<@!" + TestDataFactory.TEST_BOT_ID + "> play test song",
                    TestDataFactory.TEST_GUILD_ID,
                    List.of(TestDataFactory.TEST_BOT_ID));

            assertTrue(commands.isCommand(event), "Should detect command with bot mention using nickname format");
        }
    }

    @Test
    @DisplayName("Should not detect message with other user mentions")
    void testOtherUserMentionNotCommand() {
        try (MockedStatic<Bot> mockedBot = mockStatic(Bot.class)) {
            mockedBot.when(Bot::getClient).thenReturn(mockClient);

            MessageCreateEvent event = createMessageEventWithMentions(
                    "<@" + TestDataFactory.TEST_USER_ID + "> hello",
                    TestDataFactory.TEST_GUILD_ID,
                    List.of(TestDataFactory.TEST_USER_ID));

            assertFalse(commands.isCommand(event), "Should not detect message with other user mentions as command");
        }
    }

    // ===== ARGUMENT PARSING TESTS =====

    @Test
    @DisplayName("Should correctly strip prefix from command")
    void testStripCommandPrefix() {
        // Requirement 1.3: WHEN a command has arguments THEN the system SHALL correctly
        // parse and separate the command from its arguments
        try (MockedStatic<Bot> mockedBot = mockStatic(Bot.class)) {
            mockedBot.when(Bot::getClient).thenReturn(mockClient);

            MessageCreateEvent event = createSimpleMessageEvent("!play test song", TestDataFactory.TEST_GUILD_ID);

            String stripped = commands.stripCommandPrefixOrMentions(event);
            assertEquals("play test song", stripped, "Should strip prefix and return command with arguments");
        }
    }

    @Test
    @DisplayName("Should correctly strip bot mention from command")
    void testStripBotMention() {
        try (MockedStatic<Bot> mockedBot = mockStatic(Bot.class)) {
            mockedBot.when(Bot::getClient).thenReturn(mockClient);

            MessageCreateEvent event = createMessageEventWithMentions(
                    "<@" + TestDataFactory.TEST_BOT_ID + "> play test song",
                    TestDataFactory.TEST_GUILD_ID,
                    List.of(TestDataFactory.TEST_BOT_ID));

            String stripped = commands.stripCommandPrefixOrMentions(event);
            assertEquals("play test song", stripped, "Should strip bot mention and return command with arguments");
        }
    }

    @Test
    @DisplayName("Should correctly strip bot mention with nickname format")
    void testStripBotMentionNickname() {
        try (MockedStatic<Bot> mockedBot = mockStatic(Bot.class)) {
            mockedBot.when(Bot::getClient).thenReturn(mockClient);

            MessageCreateEvent event = createMessageEventWithMentions(
                    "<@!" + TestDataFactory.TEST_BOT_ID + "> play test song",
                    TestDataFactory.TEST_GUILD_ID,
                    List.of(TestDataFactory.TEST_BOT_ID));

            String stripped = commands.stripCommandPrefixOrMentions(event);
            assertEquals("play test song", stripped, "Should strip bot mention with nickname format");
        }
    }

    @Test
    @DisplayName("Should handle command with no arguments")
    void testCommandWithNoArguments() {
        try (MockedStatic<Bot> mockedBot = mockStatic(Bot.class)) {
            mockedBot.when(Bot::getClient).thenReturn(mockClient);

            MessageCreateEvent event = createSimpleMessageEvent("!ping", TestDataFactory.TEST_GUILD_ID);

            String stripped = commands.stripCommandPrefixOrMentions(event);
            assertEquals("ping", stripped, "Should handle command with no arguments");
        }
    }

    @Test
    @DisplayName("Should handle command with multiple arguments")
    void testCommandWithMultipleArguments() {
        try (MockedStatic<Bot> mockedBot = mockStatic(Bot.class)) {
            mockedBot.when(Bot::getClient).thenReturn(mockClient);

            MessageCreateEvent event = createSimpleMessageEvent("!play artist song title",
                    TestDataFactory.TEST_GUILD_ID);

            String stripped = commands.stripCommandPrefixOrMentions(event);
            assertEquals("play artist song title", stripped, "Should handle command with multiple arguments");
        }
    }

    @Test
    @DisplayName("Should trim whitespace from parsed command")
    void testWhitespaceTrimmingInParsing() {
        try (MockedStatic<Bot> mockedBot = mockStatic(Bot.class)) {
            mockedBot.when(Bot::getClient).thenReturn(mockClient);

            MessageCreateEvent event = createSimpleMessageEvent("!   play   test   song   ",
                    TestDataFactory.TEST_GUILD_ID);

            String stripped = commands.stripCommandPrefixOrMentions(event);
            assertEquals("play   test   song", stripped, "Should trim leading and trailing whitespace");
        }
    }

    // ===== MALFORMED COMMAND HANDLING TESTS =====

    @Test
    @DisplayName("Should handle malformed command gracefully")
    void testMalformedCommandHandling() {
        // Requirement 7.5: WHEN malformed commands are received THEN the system SHALL
        // handle them gracefully without crashing
        try (MockedStatic<Bot> mockedBot = mockStatic(Bot.class)) {
            mockedBot.when(Bot::getClient).thenReturn(mockClient);

            MessageCreateEvent event = createSimpleMessageEvent("!invalid@#$%^&*()command",
                    TestDataFactory.TEST_GUILD_ID);

            // Should not throw exception and should handle gracefully
            assertDoesNotThrow(() -> {
                boolean isCommand = commands.isCommand(event);
                assertTrue(isCommand, "Should detect malformed command as command");

                String stripped = commands.stripCommandPrefixOrMentions(event);
                assertNotNull(stripped, "Should return stripped content even for invalid commands");
                assertEquals("invalid@#$%^&*()command", stripped, "Should strip prefix from malformed command");

                // Verify the command doesn't exist in registry using both methods
                String[] parts = stripped.split("\\s+", 2);
                String commandName = parts[0].toLowerCase();
                assertNull(Commands.get(commandName), "Malformed command should not exist in command registry");
            }, "Should handle malformed commands without throwing exceptions");
        }
    }

    @Test
    @DisplayName("Should handle empty message gracefully")
    void testEmptyMessageHandling() {
        try (MockedStatic<Bot> mockedBot = mockStatic(Bot.class)) {
            mockedBot.when(Bot::getClient).thenReturn(mockClient);

            MessageCreateEvent event = createSimpleMessageEvent("", TestDataFactory.TEST_GUILD_ID);

            assertDoesNotThrow(() -> {
                boolean isCommand = commands.isCommand(event);
                assertFalse(isCommand, "Empty message should not be detected as command");
            }, "Should handle empty messages gracefully");
        }
    }

    @Test
    @DisplayName("Should handle message with only prefix")
    void testMessageWithOnlyPrefix() {
        try (MockedStatic<Bot> mockedBot = mockStatic(Bot.class)) {
            mockedBot.when(Bot::getClient).thenReturn(mockClient);

            MessageCreateEvent event = createSimpleMessageEvent("!", TestDataFactory.TEST_GUILD_ID);

            assertTrue(commands.isCommand(event), "Message with only prefix should be detected as command");

            String stripped = commands.stripCommandPrefixOrMentions(event);
            assertEquals("", stripped, "Should return empty string when stripping prefix from prefix-only message");
        }
    }

    @Test
    @DisplayName("Should handle message with special characters")
    void testMessageWithSpecialCharacters() {
        try (MockedStatic<Bot> mockedBot = mockStatic(Bot.class)) {
            mockedBot.when(Bot::getClient).thenReturn(mockClient);

            MessageCreateEvent event = createSimpleMessageEvent("!play @#$%^&*()", TestDataFactory.TEST_GUILD_ID);

            assertTrue(commands.isCommand(event), "Should detect command with special characters");

            String stripped = commands.stripCommandPrefixOrMentions(event);
            assertEquals("play @#$%^&*()", stripped, "Should preserve special characters in arguments");
        }
    }

    @Test
    @DisplayName("Should handle extremely long command input")
    void testExtremelyLongCommandInput() {
        // Create a very long command string
        String longCommand = "!play " + "a".repeat(2000);
        try (MockedStatic<Bot> mockedBot = mockStatic(Bot.class)) {
            mockedBot.when(Bot::getClient).thenReturn(mockClient);

            MessageCreateEvent event = createSimpleMessageEvent(longCommand, TestDataFactory.TEST_GUILD_ID);

            assertDoesNotThrow(() -> {
                boolean isCommand = commands.isCommand(event);
                assertTrue(isCommand, "Should detect very long command");

                String stripped = commands.stripCommandPrefixOrMentions(event);
                assertEquals("play " + "a".repeat(2000), stripped, "Should handle very long command arguments");
            }, "Should handle extremely long command input gracefully");
        }
    }

    // ===== INVALID COMMAND HANDLING TESTS =====

    @Test
    @DisplayName("Should handle valid command correctly")
    void testValidCommandHandling() {
        // Requirement 1.4: Valid commands should be processed normally
        try (MockedStatic<Bot> mockedBot = mockStatic(Bot.class)) {
            mockedBot.when(Bot::getClient).thenReturn(mockClient);

            MessageCreateEvent event = createSimpleMessageEvent("!play test song", TestDataFactory.TEST_GUILD_ID);

            assertTrue(commands.isCommand(event), "Should detect valid command as command");

            String stripped = commands.stripCommandPrefixOrMentions(event);
            assertEquals("play test song", stripped, "Should parse valid command normally");

            // Verify the command exists in registry using both methods
            String[] parts = stripped.split("\\s+", 2);
            String commandName = parts[0].toLowerCase();
            assertNotNull(Commands.get(commandName), "Valid command should exist in command registry");

            // Verify arguments are parsed correctly
            assertEquals("play", parts[0], "Command name should be parsed correctly");
            assertEquals("test song", parts[1], "Arguments should be parsed correctly");
        }
    }

    @Test
    @DisplayName("Should handle invalid command gracefully")
    void testInvalidCommandHandling() {
        // Requirement 1.4: WHEN an invalid command is received THEN the system SHALL
        // handle it gracefully without errors
        try (MockedStatic<Bot> mockedBot = mockStatic(Bot.class)) {
            mockedBot.when(Bot::getClient).thenReturn(mockClient);

            MessageCreateEvent event = createSimpleMessageEvent("!nonexistentcommand arg1 arg2",
                    TestDataFactory.TEST_GUILD_ID);

            assertTrue(commands.isCommand(event), "Should detect invalid command as command");

            String stripped = commands.stripCommandPrefixOrMentions(event);
            assertEquals("nonexistentcommand arg1 arg2", stripped,
                    "Should parse invalid command and return stripped content");

            // Verify the command doesn't exist in registry but parsing still works
            String[] parts = stripped.split("\\s+", 2);
            String commandName = parts[0].toLowerCase();
            assertNull(Commands.get(commandName), "Invalid command should return null from command registry");

            // Verify arguments are still parsed correctly
            assertEquals("nonexistentcommand", parts[0], "Command name should be parsed correctly");
            assertEquals("arg1 arg2", parts[1], "Arguments should be parsed correctly");
        }
    }

    @Test
    @DisplayName("Should handle null message content gracefully")
    void testNullMessageContentHandling() {
        try (MockedStatic<Bot> mockedBot = mockStatic(Bot.class)) {
            mockedBot.when(Bot::getClient).thenReturn(mockClient);

            MessageCreateEvent event = createSimpleMessageEvent(null, TestDataFactory.TEST_GUILD_ID);

            assertDoesNotThrow(() -> {
                boolean isCommand = commands.isCommand(event);
                assertFalse(isCommand, "Null message should not be detected as command");
            }, "Should handle null message content gracefully");
        }
    }

    // ===== ARGUMENT PARSING AND VALIDATION TESTS =====

    @Test
    @DisplayName("Should parse command arguments correctly")
    void testArgumentParsing() {
        // Requirement 1.3: WHEN a command has arguments THEN the system SHALL correctly
        // parse and separate the command from its arguments
        try (MockedStatic<Bot> mockedBot = mockStatic(Bot.class)) {
            mockedBot.when(Bot::getClient).thenReturn(mockClient);

            MessageCreateEvent event = createSimpleMessageEvent("!play artist song title",
                    TestDataFactory.TEST_GUILD_ID);

            // Test that the command is detected
            assertTrue(commands.isCommand(event), "Should detect command with arguments");

            // Test that arguments are parsed correctly
            String stripped = commands.stripCommandPrefixOrMentions(event);
            String[] args = stripped.trim().split("\\s+");

            assertEquals("play", args[0], "First argument should be the command");
            assertEquals("artist", args[1], "Second argument should be 'artist'");
            assertEquals("song", args[2], "Third argument should be 'song'");
            assertEquals("title", args[3], "Fourth argument should be 'title'");
        }
    }

    @Test
    @DisplayName("Should handle commands with no arguments - detailed parsing")
    void testCommandWithNoArgumentsDetailed() {
        // Requirement 1.3: Commands with no arguments should be parsed correctly
        try (MockedStatic<Bot> mockedBot = mockStatic(Bot.class)) {
            mockedBot.when(Bot::getClient).thenReturn(mockClient);

            MessageCreateEvent event = createSimpleMessageEvent("!ping", TestDataFactory.TEST_GUILD_ID);

            assertTrue(commands.isCommand(event), "Should detect command without arguments");

            String stripped = commands.stripCommandPrefixOrMentions(event);
            String[] args = stripped.trim().split("\\s+");

            assertEquals(1, args.length, "Should have only one argument (the command)");
            assertEquals("ping", args[0], "Command should be 'ping'");
        }
    }

    @Test
    @DisplayName("Should handle commands with quoted arguments")
    void testCommandWithQuotedArguments() {
        try (MockedStatic<Bot> mockedBot = mockStatic(Bot.class)) {
            mockedBot.when(Bot::getClient).thenReturn(mockClient);

            MessageCreateEvent event = createSimpleMessageEvent("!play \"song with spaces\" artist",
                    TestDataFactory.TEST_GUILD_ID);

            assertTrue(commands.isCommand(event), "Should detect command with quoted arguments");

            String stripped = commands.stripCommandPrefixOrMentions(event);
            // Note: The current implementation doesn't handle quotes specially, so this
            // tests the actual behavior
            String[] args = stripped.trim().split("\\s+");

            assertEquals("play", args[0], "Command should be 'play'");
            assertEquals("\"song", args[1], "Should split on spaces even within quotes");
            assertEquals("with", args[2], "Should continue splitting");
            assertEquals("spaces\"", args[3], "Should include closing quote");
        }
    }

    @Test
    @DisplayName("Should handle commands with extra whitespace - argument parsing")
    void testCommandWithExtraWhitespaceArguments() {
        try (MockedStatic<Bot> mockedBot = mockStatic(Bot.class)) {
            mockedBot.when(Bot::getClient).thenReturn(mockClient);

            MessageCreateEvent event = createSimpleMessageEvent("!play    artist     song",
                    TestDataFactory.TEST_GUILD_ID);

            assertTrue(commands.isCommand(event), "Should detect command with extra whitespace");

            String stripped = commands.stripCommandPrefixOrMentions(event);
            String[] args = stripped.trim().split("\\s+");

            assertEquals("play", args[0], "Command should be 'play'");
            assertEquals("artist", args[1], "Should handle extra spaces between arguments");
            assertEquals("song", args[2], "Should handle extra spaces between arguments");
            assertEquals(3, args.length, "Should have correct number of arguments despite extra spaces");
        }
    }

    @Test
    @DisplayName("Should handle empty arguments gracefully")
    void testEmptyArguments() {
        try (MockedStatic<Bot> mockedBot = mockStatic(Bot.class)) {
            mockedBot.when(Bot::getClient).thenReturn(mockClient);

            MessageCreateEvent event = createSimpleMessageEvent("!play ", TestDataFactory.TEST_GUILD_ID);

            assertTrue(commands.isCommand(event), "Should detect command with trailing space");

            String stripped = commands.stripCommandPrefixOrMentions(event);
            String[] args = stripped.trim().split("\\s+");

            assertEquals(1, args.length, "Should have only the command when no arguments provided");
            assertEquals("play", args[0], "Command should be 'play'");
        }
    }

    @Test
    @DisplayName("Should validate command format")
    void testCommandFormatValidation() {
        // Requirement 1.4: WHEN an invalid command is received THEN the system SHALL
        // handle it gracefully without errors
        try (MockedStatic<Bot> mockedBot = mockStatic(Bot.class)) {
            mockedBot.when(Bot::getClient).thenReturn(mockClient);

            // Test various command formats
            String[] testCommands = {
                    "!valid-command",
                    "!command123",
                    "!UPPERCASE",
                    "!mixed_Case-Command",
                    "!command.with.dots"
            };

            for (String commandText : testCommands) {
                MessageCreateEvent event = createSimpleMessageEvent(commandText, TestDataFactory.TEST_GUILD_ID);

                assertDoesNotThrow(() -> {
                    boolean isCommand = commands.isCommand(event);
                    assertTrue(isCommand, "Should detect command: " + commandText);

                    String stripped = commands.stripCommandPrefixOrMentions(event);
                    assertNotNull(stripped, "Stripped command should not be null");
                }, "Should handle command format gracefully: " + commandText);
            }
        }
    }

    @Test
    @DisplayName("Should handle malformed command arguments")
    void testMalformedCommandArguments() {
        // Requirement 7.5: WHEN malformed commands are received THEN the system SHALL
        // handle them gracefully without crashing
        try (MockedStatic<Bot> mockedBot = mockStatic(Bot.class)) {
            mockedBot.when(Bot::getClient).thenReturn(mockClient);

            String[] malformedCommands = {
                    "!command\t\ttab\tseparated",
                    "!command\nwith\nnewlines",
                    "!command\r\nwith\r\ncarriage\r\nreturns",
                    "!command with unicode: 🎵🎶",
                    "!command with symbols: @#$%^&*()",
                    "!command with numbers: 123456789"
            };

            for (String malformedCommand : malformedCommands) {
                MessageCreateEvent event = createSimpleMessageEvent(malformedCommand, TestDataFactory.TEST_GUILD_ID);

                assertDoesNotThrow(() -> {
                    boolean isCommand = commands.isCommand(event);
                    assertTrue(isCommand, "Should detect malformed command: " + malformedCommand);

                    String stripped = commands.stripCommandPrefixOrMentions(event);
                    assertNotNull(stripped, "Should handle malformed arguments without crashing");
                }, "Should handle malformed command gracefully: " + malformedCommand);
            }
        }
    }

    @Test
    @DisplayName("Should handle very long argument lists")
    void testVeryLongArgumentLists() {
        try (MockedStatic<Bot> mockedBot = mockStatic(Bot.class)) {
            mockedBot.when(Bot::getClient).thenReturn(mockClient);

            // Create a command with many arguments
            StringBuilder longCommand = new StringBuilder("!play");
            for (int i = 0; i < 50; i++) {
                longCommand.append(" arg").append(i);
            }

            MessageCreateEvent event = createSimpleMessageEvent(longCommand.toString(), TestDataFactory.TEST_GUILD_ID);

            assertDoesNotThrow(() -> {
                boolean isCommand = commands.isCommand(event);
                assertTrue(isCommand, "Should detect command with many arguments");

                String stripped = commands.stripCommandPrefixOrMentions(event);
                String[] args = stripped.trim().split("\\s+");

                assertEquals(51, args.length, "Should have correct number of arguments (command + 50 args)");
                assertEquals("play", args[0], "First argument should be the command");
                assertEquals("arg0", args[1], "Second argument should be 'arg0'");
                assertEquals("arg49", args[50], "Last argument should be 'arg49'");
            }, "Should handle very long argument lists gracefully");
        }
    }

    @Test
    @DisplayName("Should preserve argument order")
    void testArgumentOrderPreservation() {
        try (MockedStatic<Bot> mockedBot = mockStatic(Bot.class)) {
            mockedBot.when(Bot::getClient).thenReturn(mockClient);

            MessageCreateEvent event = createSimpleMessageEvent("!play first second third fourth fifth",
                    TestDataFactory.TEST_GUILD_ID);

            String stripped = commands.stripCommandPrefixOrMentions(event);
            String[] args = stripped.trim().split("\\s+");

            assertEquals("play", args[0], "Command should be first");
            assertEquals("first", args[1], "Arguments should maintain order");
            assertEquals("second", args[2], "Arguments should maintain order");
            assertEquals("third", args[3], "Arguments should maintain order");
            assertEquals("fourth", args[4], "Arguments should maintain order");
            assertEquals("fifth", args[5], "Arguments should maintain order");
        }
    }
}