package net.iotku.subdonic.bot;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class to verify that our mock factories work correctly.
 * This ensures the core testing infrastructure is functioning properly.
 */
class MockFactoryTest {

    @Test
    void testMockDiscordClientFactory() {
        // Test creating a mock client
        GatewayDiscordClient mockClient = MockDiscordClientFactory.createMockClient(TestDataFactory.TEST_OWNER_ID);
        
        assertNotNull(mockClient);
        assertEquals(TestDataFactory.TEST_BOT_ID, mockClient.getSelfId().asLong());
    }

    @Test
    void testTestEventBuilderGuildMessage() {
        // Test creating a guild message
        MessageCreateEvent event = TestEventBuilder.createGuildMessage("!play test song");
        
        assertNotNull(event);
        assertNotNull(event.getMessage());
        assertEquals("!play test song", event.getMessage().getContent());
        assertTrue(event.getGuildId().isPresent());
        assertEquals(TestDataFactory.TEST_GUILD_ID, event.getGuildId().get().asLong());
    }

    @Test
    void testTestEventBuilderDMMessage() {
        // Test creating a DM message
        MessageCreateEvent event = TestEventBuilder.createDMMessage("!play test song");
        
        assertNotNull(event);
        assertNotNull(event.getMessage());
        assertEquals("!play test song", event.getMessage().getContent());
        assertFalse(event.getGuildId().isPresent());
    }

    @Test
    void testTestEventBuilderVoiceUserMessage() {
        // Test creating a message from user in voice
        MessageCreateEvent event = TestEventBuilder.createVoiceUserMessage("!play test song");
        
        assertNotNull(event);
        assertNotNull(event.getMessage());
        assertEquals("!play test song", event.getMessage().getContent());
        assertTrue(event.getMember().isPresent());
    }

    @Test
    void testTestDataFactoryStandardMessages() {
        // Test standard message creation
        MessageCreateEvent guildMessage = TestDataFactory.createStandardGuildMessage();
        MessageCreateEvent dmMessage = TestDataFactory.createStandardDMMessage();
        MessageCreateEvent ownerMessage = TestDataFactory.createOwnerMessage();
        
        assertNotNull(guildMessage);
        assertNotNull(dmMessage);
        assertNotNull(ownerMessage);
        
        // Verify guild message has guild context
        assertTrue(guildMessage.getGuildId().isPresent());
        
        // Verify DM message has no guild context
        assertFalse(dmMessage.getGuildId().isPresent());
        
        // Verify owner message has correct user ID
        assertEquals(TestDataFactory.TEST_OWNER_ID, 
                ownerMessage.getMessage().getAuthor().get().getId().asLong());
    }

    @Test
    void testTestDataFactorySongCreation() {
        // Test song creation
        var song = TestDataFactory.createTestSong();
        
        assertNotNull(song);
        assertEquals(TestDataFactory.TEST_TITLE, song.title());
        assertEquals(TestDataFactory.TEST_ARTIST, song.artist());
        assertEquals(TestDataFactory.TEST_ALBUM, song.album());
        assertEquals("1", song.id());
    }

    @Test
    void testTestDataFactoryMultipleSongs() {
        // Test creating multiple songs
        var songs = TestDataFactory.createTestSongs(3);
        
        assertNotNull(songs);
        assertEquals(3, songs.length);
        
        for (int i = 0; i < songs.length; i++) {
            assertEquals(String.valueOf(i + 1), songs[i].id());
            assertTrue(songs[i].title().contains(String.valueOf(i + 1)));
        }
    }

    @Test
    void testTestEventBuilderFluentAPI() {
        // Test the fluent API
        MessageCreateEvent event = new TestEventBuilder()
                .withMessage("!search test query")
                .withUser(TestDataFactory.TEST_SECOND_USER_ID)
                .withGuild(TestDataFactory.TEST_GUILD_ID)
                .withChannel(TestDataFactory.TEST_CHANNEL_ID)
                .withUserInVoice(true)
                .withVoiceChannel(TestDataFactory.TEST_VOICE_CHANNEL_ID)
                .build();
        
        assertNotNull(event);
        assertEquals("!search test query", event.getMessage().getContent());
        assertEquals(TestDataFactory.TEST_SECOND_USER_ID, 
                event.getMessage().getAuthor().get().getId().asLong());
        assertTrue(event.getGuildId().isPresent());
        assertEquals(TestDataFactory.TEST_GUILD_ID, event.getGuildId().get().asLong());
    }

    @Test
    void testTestEventBuilderBotMention() {
        // Test bot mention functionality
        MessageCreateEvent event = TestEventBuilder.createBotMentionMessage("play test");
        
        assertNotNull(event);
        assertTrue(event.getMessage().getContent().contains(String.valueOf(TestDataFactory.TEST_BOT_ID)));
        assertFalse(event.getMessage().getUserMentionIds().isEmpty());
    }
}