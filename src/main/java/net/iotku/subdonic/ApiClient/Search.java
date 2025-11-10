package net.iotku.subdonic.ApiClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import net.iotku.subdonic.api.v1.dto.RankedSong;
import net.iotku.subdonic.api.v1.dto.Song;
import net.iotku.subdonic.bot.MessageCtx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Search {
    private static final Logger log = LoggerFactory.getLogger(Search.class);

    public static List<Song> search3(MessageCtx ctx, String query) throws IOException, InterruptedException {
        String url = Http.baseUrl + "subsonic/search3?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        HttpResponse<String> response = Http.makeGetRequest(url);

        if (response.statusCode() != 200) {
            log.warn("Subsonic search failed: {}", response.body());
            return Collections.emptyList();
        }

        List<Song> results;
        try {
            results = Arrays.asList(Http.MAPPER.readValue(response.body(), Song[].class));
            log.info("({}:{}) {}: {} results found for search {}", ctx.guildId().asLong(), ctx.channelId().asLong(), ctx.memberId().asLong(), results.size(), query);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse JSON from Subsonic API", e);
            return Collections.emptyList();
        }

        // Rank songs and return
        return results.stream()
                .map(song -> {
                    int score = 0;
                    if (song.title() != null && song.title().toLowerCase().contains(query.toLowerCase())) {
                        score += 10; // big weight for title match
                    }
                    if (song.album() != null && song.album().toLowerCase().contains(query.toLowerCase())) {
                        score += 1; // We probably aren't looking for an album so not super important
                    }
                    if (song.album() != null && song.album().toLowerCase().contains("live")) {
                        score -= 5; // Deprioritize live albums
                    }
                    if (song.artist() != null && song.artist().toLowerCase().contains(query.toLowerCase())) {
                        score += 5;
                    }
                    return new RankedSong(song, score);
                })
                .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                .map(RankedSong::song)
                .toList();
    }

    /**
     * Get random songs from the Subsonic API
     * @param ctx MessageCtx to track usage
     * @param size how many random tracks to request from the API
     * @return a List of Songs returned from the API
     * @throws IOException           if an I/O error occurs while sending the request
     *                               or reading the response from the Subsonic API
     * @throws InterruptedException  if the calling thread is interrupted while waiting
     *                               for the HTTP request to complete
     */
    public static List<Song> random(MessageCtx ctx, int size) throws IOException, InterruptedException {
        String url = Http.baseUrl + "subsonic/getRandomSongs?size=" + URLEncoder.encode(Integer.toString(size), StandardCharsets.UTF_8);
        HttpResponse<String> response = Http.makeGetRequest(url);

        if (response.statusCode() != 200) {
            log.warn("Subsonic random failed: {}", response.body());
            return Collections.emptyList();
        }

        List<Song> results;
        try {
            results = Arrays.asList(Http.MAPPER.readValue(response.body(), Song[].class));
            log.info("{} | {} random songs found. Requested {}", ctx, results.size(), size);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse JSON from Subsonic API", e);
            return Collections.emptyList();
        }
        return results;
    }

}
