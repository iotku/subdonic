package net.iotku.subdonic.bot;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import discord4j.common.util.Snowflake;
import net.iotku.subdonic.api.v1.dto.Song;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrackLoadHandler implements AudioLoadResultHandler {
    private static final Logger log = LoggerFactory.getLogger(TrackLoadHandler.class);
    private final GuildAudioManager manager;
    private final Song song;

    TrackLoadHandler(Song song, Snowflake guildId) {
        this.manager = GuildAudioManager.of(guildId);
        this.song = song;
    }

    @Override
    public void trackLoaded(AudioTrack track) {
        track.setUserData(song);
        manager.getScheduler().play(track);
    }

    @Override public void playlistLoaded(AudioPlaylist playlist) {}
    @Override public void noMatches() {}
    @Override public void loadFailed(FriendlyException exception) {
        log.error("Loading track failed: {}", exception.getMessage());
    }
}
