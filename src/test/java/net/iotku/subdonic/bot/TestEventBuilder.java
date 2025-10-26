package net.iotku.subdonic.bot;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.entity.channel.VoiceChannel;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

/**
 * Fluent API builder for creating MessageCreateEvent mocks with realistic Discord behavior.
 * Provides a convenient way to build test scenarios with various configurations.
 */
public class TestEventBuilder {
    private String messageContent = "";
    private Long userId = TestDataFactory.TEST_USER_ID;
    private Long guildId = TestDataFactory.TEST_GUILD_ID;
    private Long channelId = TestDataFactory.TEST_CHANNEL_ID;
    private boolean userInVoice = false;
    private boolean botInVoice = false;
    private Long voiceChannelId = TestDataFactory.TEST_VOICE_CHANNEL_ID;
    private boolean isDM = false;
    private List<Snowflake> mentionedUsers = new ArrayList<>();

    /**
     * Sets the message content.
     *
     * @param content The message content
     * @return This builder for method chaining
     */
    public TestEventBuilder withMessage(String content) {
        this.messageContent = content;
        return this;
    }

    /**
     * Sets the user ID of the message author.
     *
     * @param userId The user ID
     * @return This builder for method chaining
     */
    public TestEventBuilder withUser(Long userId) {
        this.userId = userId;
        return this;
    }

    /**
     * Sets the guild ID. Set to null for DM messages.
     *
     * @param guildId The guild ID
     * @return This builder for method chaining
     */
    public TestEventBuilder withGuild(Long guildId) {
        this.guildId = guildId;
        this.isDM = (guildId == null);
        return this;
    }

    /**
     * Sets the channel ID.
     *
     * @param channelId The channel ID
     * @return This builder for method chaining
     */
    public TestEventBuilder withChannel(Long channelId) {
        this.channelId = channelId;
        return this;
    }

    /**
     * Sets whether the user is in a voice channel.
     *
     * @param inVoice True if user is in voice channel
     * @return This builder for method chaining
     */
    public TestEventBuilder withUserInVoice(boolean inVoice) {
        this.userInVoice = inVoice;
        return this;
    }

    /**
     * Sets whether the bot is in a voice channel.
     *
     * @param inVoice True if bot is in voice channel
     * @return This builder for method chaining
     */
    public TestEventBuilder withBotInVoice(boolean inVoice) {
        this.botInVoice = inVoice;
        return this;
    }

    /**
     * Sets the voice channel ID for voice-related tests.
     *
     * @param voiceChannelId The voice channel ID
     * @return This builder for method chaining
     */
    public TestEventBuilder withVoiceChannel(Long voiceChannelId) {
        this.voiceChannelId = voiceChannelId;
        return this;
    }

    /**
     * Configures the message as a DM (Direct Message).
     *
     * @return This builder for method chaining
     */
    public TestEventBuilder asDM() {
        this.isDM = true;
        this.guildId = null;
        return this;
    }

    /**
     * Adds a user mention to the message.
     *
     * @param mentionedUserId The ID of the mentioned user
     * @return This builder for method chaining
     */
    public TestEventBuilder withMention(Long mentionedUserId) {
        this.mentionedUsers.add(Snowflake.of(mentionedUserId));
        return this;
    }

    /**
     * Adds a bot mention to the message (useful for testing bot mention commands).
     *
     * @return This builder for method chaining
     */
    public TestEventBuilder withBotMention() {
        this.mentionedUsers.add(Snowflake.of(TestDataFactory.TEST_BOT_ID));
        return this;
    }

    /**
     * Builds the MessageCreateEvent with the configured parameters.
     *
     * @return A configured mock MessageCreateEvent
     */
    public MessageCreateEvent build() {
        MessageCreateEvent mockEvent = mock(MessageCreateEvent.class);
        Message mockMessage = mock(Message.class);
        User mockUser = mock(User.class);
        MessageChannel mockChannel = MockDiscordClientFactory.createMockMessageChannel(channelId);

        // Configure message content and author
        when(mockMessage.getContent()).thenReturn(messageContent);
        when(mockMessage.getAuthor()).thenReturn(Optional.of(mockUser));
        when(mockUser.getId()).thenReturn(Snowflake.of(userId));

        // Configure channel
        when(mockMessage.getChannelId()).thenReturn(Snowflake.of(channelId));
        when(mockMessage.getChannel()).thenReturn(Mono.just(mockChannel));
        when(mockEvent.getMessage()).thenReturn(mockMessage);

        // Configure mentions
        when(mockMessage.getUserMentionIds()).thenReturn(mentionedUsers);

        // Configure guild context (DM vs Guild)
        if (isDM || guildId == null) {
            when(mockEvent.getGuildId()).thenReturn(Optional.empty());
            when(mockEvent.getMember()).thenReturn(Optional.empty());
        } else {
            when(mockEvent.getGuildId()).thenReturn(Optional.of(Snowflake.of(guildId)));
            
            // Create mock member with voice state
            Member mockMember = createMockMemberWithVoiceState();
            when(mockEvent.getMember()).thenReturn(Optional.of(mockMember));
        }

        return mockEvent;
    }

    /**
     * Creates a mock member with the appropriate voice state based on builder configuration.
     *
     * @return A configured mock Member
     */
    private Member createMockMemberWithVoiceState() {
        Member mockMember = mock(Member.class);
        VoiceState mockVoiceState = mock(VoiceState.class);

        when(mockMember.getId()).thenReturn(Snowflake.of(userId));
        when(mockMember.getGuildId()).thenReturn(Snowflake.of(guildId));
        when(mockMember.getVoiceState()).thenReturn(Mono.just(mockVoiceState));

        if (userInVoice) {
            VoiceChannel mockVoiceChannel = MockDiscordClientFactory.createMockVoiceChannel(voiceChannelId, guildId);
            when(mockVoiceState.getChannel()).thenReturn(Mono.just(mockVoiceChannel));
        } else {
            when(mockVoiceState.getChannel()).thenReturn(Mono.empty());
        }

        return mockMember;
    }

    /**
     * Creates a standard guild message event for testing.
     *
     * @param content The message content
     * @return A configured MessageCreateEvent for guild context
     */
    public static MessageCreateEvent createGuildMessage(String content) {
        return new TestEventBuilder()
                .withMessage(content)
                .build();
    }

    /**
     * Creates a standard DM message event for testing.
     *
     * @param content The message content
     * @return A configured MessageCreateEvent for DM context
     */
    public static MessageCreateEvent createDMMessage(String content) {
        return new TestEventBuilder()
                .withMessage(content)
                .asDM()
                .build();
    }

    /**
     * Creates a message event from a user in a voice channel.
     *
     * @param content The message content
     * @return A configured MessageCreateEvent with user in voice
     */
    public static MessageCreateEvent createVoiceUserMessage(String content) {
        return new TestEventBuilder()
                .withMessage(content)
                .withUserInVoice(true)
                .build();
    }

    /**
     * Creates a message event with bot mention.
     *
     * @param content The message content
     * @return A configured MessageCreateEvent with bot mention
     */
    public static MessageCreateEvent createBotMentionMessage(String content) {
        return new TestEventBuilder()
                .withMessage("<@" + TestDataFactory.TEST_BOT_ID + "> " + content)
                .withBotMention()
                .build();
    }
}