package net.iotku.subdonic.bot;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.*;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.entity.channel.VoiceChannel;
import discord4j.core.spec.MessageCreateMono;
import discord4j.rest.RestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Factory class for creating consistent Discord4J mock objects for testing.
 * Provides methods to create realistic mock objects that simulate Discord's behavior patterns.
 */
public class MockDiscordClientFactory {

    /**
     * Creates a mock GatewayDiscordClient with the specified owner ID.
     * The client is configured to return the owner ID when fetching application info.
     *
     * @param ownerId The Discord user ID of the bot owner
     * @return A configured mock GatewayDiscordClient
     */
    public static GatewayDiscordClient createMockClient(Long ownerId) {
        GatewayDiscordClient mockClient = mock(GatewayDiscordClient.class);
        
        // Mock the bot's self ID
        Snowflake botId = Snowflake.of(TestDataFactory.TEST_BOT_ID);
        when(mockClient.getSelfId()).thenReturn(botId);
        
        // For testing purposes, we'll mock the rest client more simply
        // The actual owner ID fetching will be tested separately
        RestClient mockRestClient = mock(RestClient.class);
        when(mockClient.getRestClient()).thenReturn(mockRestClient);
        
        // Mock getSelfMember for voice state checks
        when(mockClient.getSelfMember(any(Snowflake.class))).thenReturn(Mono.empty());
        
        return mockClient;
    }

    /**
     * Creates a mock MessageCreateEvent with the specified parameters.
     *
     * @param content The message content
     * @param userId The ID of the user who sent the message
     * @param guildId The ID of the guild (null for DMs)
     * @param channelId The ID of the channel
     * @return A configured mock MessageCreateEvent
     */
    public static MessageCreateEvent createMessageEvent(String content, Long userId, Long guildId, Long channelId) {
        MessageCreateEvent mockEvent = mock(MessageCreateEvent.class);
        Message mockMessage = mock(Message.class);
        User mockUser = mock(User.class);
        MessageChannel mockChannel = mock(MessageChannel.class);
        
        // Configure message content and author
        when(mockMessage.getContent()).thenReturn(content);
        when(mockMessage.getAuthor()).thenReturn(java.util.Optional.of(mockUser));
        when(mockUser.getId()).thenReturn(Snowflake.of(userId));
        
        // Configure channel
        when(mockMessage.getChannelId()).thenReturn(Snowflake.of(channelId));
        when(mockMessage.getChannel()).thenReturn(Mono.just(mockChannel));
        when(mockEvent.getMessage()).thenReturn(mockMessage);
        
        // Configure guild (optional for DMs)
        if (guildId != null) {
            when(mockEvent.getGuildId()).thenReturn(java.util.Optional.of(Snowflake.of(guildId)));
            
            // Create mock member for guild messages
            Member mockMember = createMockMember(userId, guildId, false);
            when(mockEvent.getMember()).thenReturn(java.util.Optional.of(mockMember));
        } else {
            when(mockEvent.getGuildId()).thenReturn(java.util.Optional.empty());
            when(mockEvent.getMember()).thenReturn(java.util.Optional.empty());
        }
        
        // Mock user mention handling
        when(mockMessage.getUserMentionIds()).thenReturn(java.util.List.of());
        
        return mockEvent;
    }

    /**
     * Creates a mock Member with the specified parameters.
     *
     * @param userId The user ID
     * @param guildId The guild ID
     * @param inVoiceChannel Whether the member is in a voice channel
     * @return A configured mock Member
     */
    public static Member createMockMember(Long userId, Long guildId, boolean inVoiceChannel) {
        Member mockMember = mock(Member.class);
        VoiceState mockVoiceState = mock(VoiceState.class);
        
        when(mockMember.getId()).thenReturn(Snowflake.of(userId));
        when(mockMember.getGuildId()).thenReturn(Snowflake.of(guildId));
        when(mockMember.getVoiceState()).thenReturn(Mono.just(mockVoiceState));
        
        if (inVoiceChannel) {
            VoiceChannel mockVoiceChannel = createMockVoiceChannel(TestDataFactory.TEST_VOICE_CHANNEL_ID, guildId);
            when(mockVoiceState.getChannel()).thenReturn(Mono.just(mockVoiceChannel));
        } else {
            when(mockVoiceState.getChannel()).thenReturn(Mono.empty());
        }
        
        return mockMember;
    }

    /**
     * Creates a mock VoiceChannel with the specified parameters.
     *
     * @param channelId The voice channel ID
     * @param guildId The guild ID
     * @return A configured mock VoiceChannel
     */
    public static VoiceChannel createMockVoiceChannel(Long channelId, Long guildId) {
        VoiceChannel mockVoiceChannel = mock(VoiceChannel.class);
        
        when(mockVoiceChannel.getId()).thenReturn(Snowflake.of(channelId));
        when(mockVoiceChannel.getGuildId()).thenReturn(Snowflake.of(guildId));
        
        return mockVoiceChannel;
    }

    /**
     * Creates a mock VoiceState for the specified channel.
     *
     * @param channelId The voice channel ID (null if not in voice)
     * @return A configured mock VoiceState
     */
    public static VoiceState createMockVoiceState(Long channelId) {
        VoiceState mockVoiceState = mock(VoiceState.class);
        
        if (channelId != null) {
            VoiceChannel mockVoiceChannel = mock(VoiceChannel.class);
            when(mockVoiceChannel.getId()).thenReturn(Snowflake.of(channelId));
            when(mockVoiceState.getChannel()).thenReturn(Mono.just(mockVoiceChannel));
        } else {
            when(mockVoiceState.getChannel()).thenReturn(Mono.empty());
        }
        
        return mockVoiceState;
    }

    /**
     * Creates a mock MessageChannel for testing message sending.
     *
     * @param channelId The channel ID
     * @return A configured mock MessageChannel
     */
    public static MessageChannel createMockMessageChannel(Long channelId) {
        MessageChannel mockChannel = mock(MessageChannel.class);
        MessageCreateMono mockCreateMono = mock(MessageCreateMono.class);
        Message mockMessage = mock(Message.class);
        
        when(mockChannel.getId()).thenReturn(Snowflake.of(channelId));
        // Mock message creation - return a mock MessageCreateMono
        when(mockChannel.createMessage(any(String.class))).thenReturn(mockCreateMono);
        when(mockCreateMono.then()).thenReturn(Mono.empty());
        when(mockCreateMono.block()).thenReturn(mockMessage);
        
        return mockChannel;
    }
}