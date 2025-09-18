package net.iotku.subdonic.subsonic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Song(String title, String artist, String album, String year, String id) {
}
