package net.iotku.subdonic.api.v1.filter;
import net.iotku.subdonic.api.v1.dto.Song;
import org.subsonic.restapi.Child;

import java.util.Objects;
import java.util.function.Predicate;

public class SubsonicFilter {
    public static final Predicate<Child> taglessChild =
        song -> !Objects.equals(song.getArtist(), "[Unknown Artist]") && !song.getTitle().startsWith("/");

    public static final Predicate<Song> taglessSong =
            song -> !Objects.equals(song.artist(), "[Unknown Artist]") && !song.title().startsWith("/");
}
