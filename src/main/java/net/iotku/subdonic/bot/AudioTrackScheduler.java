package net.iotku.subdonic.bot;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import discord4j.common.util.Snowflake;
import net.iotku.subdonic.api.v1.dto.Song;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import static net.iotku.subdonic.ApiClient.Stream.getStreamUrl;

public class AudioTrackScheduler extends AudioEventAdapter {

    private final Queue<AudioTrack> queue = new ConcurrentLinkedQueue<>();
    private final AudioPlayer player;
    private Consumer<AudioTrack> onTrackStart;
    private Consumer<AudioTrack> onTrackAdd;
    private static final Logger log = LoggerFactory.getLogger(AudioTrackScheduler.class);

    public AudioTrackScheduler(AudioPlayer player) {
        this.player = player;
    }

    static Song loadTrack(Song song, Snowflake guildId) {
        GuildAudioManager.getPlayerManager().loadItem(getStreamUrl(song), new TrackLoadHandler(song, guildId));
        return song;
    }

    public Queue<AudioTrack> getQueue() {
        return queue;
    }

    public boolean play(AudioTrack track) {
        return play(track, false);
    }

    public boolean play(AudioTrack track, boolean force) {
        boolean playing = player.startTrack(track, !force);

        Song song = (Song) track.getUserData(); // extract the Song from the AudioTrack

        if (!playing) {
            queue.add(track);
            if (onTrackAdd != null) onTrackAdd.accept(track);
            log.info("Added: {} - {}", song.artist(), song.title());
        } else {
            if (onTrackStart != null) onTrackStart.accept(track);
            log.info("Playing: {}", song);
        }

        return playing;
    }

    public boolean skip() {
        AudioTrack next = queue.poll(); // removes head of queue, or null if empty
        return next != null && play(next, true);
    }

    public boolean skip(int count) {
        if (count < 1 || count > queue.size()) {
            return false; // invalid count
        }
        for (int i = 0; i < count - 1; i++) {
            queue.poll(); // discard
        }
        AudioTrack next = queue.poll();
        return next != null && play(next, true);
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        // Advance the player if the track completed naturally (FINISHED) or if the track cannot play (LOAD_FAILED)
        if (endReason.mayStartNext) {
            skip();
        }
    }

    // Setter for the consumers
    public void setOnTrackStart(Consumer<AudioTrack> onTrackStart) {
        this.onTrackStart = onTrackStart;
    }

    public void setOnTrackAdd(Consumer<AudioTrack> onTrackAdd) {
        this.onTrackAdd = onTrackAdd;
    }
}