package net.iotku.subdonic.api.v1.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Song(String title, String artist, String album, String year, String id) {
    public static final Song DEFAULT = new Song(
            "No song playing",
            "",
            "",
            "",
            ""
    );
}
