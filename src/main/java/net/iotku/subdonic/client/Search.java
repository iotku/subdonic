package net.iotku.subdonic.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final String baseUrl = "http://localhost:8080/api/v1/";
    private static final Logger log = LoggerFactory.getLogger(Search.class);

    public static List<Song> search3(MessageCtx ctx, String query) throws IOException, InterruptedException {
        ObjectMapper mapper = new ObjectMapper();
        String url = baseUrl + "subsonic/search3?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        HttpResponse<String> response = Http.makeRequest(url);

        if (response.statusCode() != 200) {
            log.warn("Subsonic search failed: {}", response.body());
            return Collections.emptyList();
        }

        try {
            List<Song> results = Arrays.asList(mapper.readValue(response.body(), Song[].class));
            log.info("({}:{}) {}: {} results found for search {}", ctx.guildId().asLong(), ctx.channelId().asLong(), ctx.memberId().asLong(), results.size(), query);
            return results;
        } catch (JsonProcessingException e) {
            log.error("Failed to parse JSON from Subsonic API", e);
            return Collections.emptyList();
        }
    }
}
