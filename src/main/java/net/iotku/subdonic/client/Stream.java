package net.iotku.subdonic.client;

import net.iotku.subdonic.api.v1.dto.Song;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class Stream
{
    private static final String baseUrl = "http://localhost:8080/api/v1/";

    public static String getStreamUrl(Song song) {
        return baseUrl + "subsonic/stream/" + URLEncoder.encode(song.id(), StandardCharsets.UTF_8);
    }
}
