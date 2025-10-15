package net.iotku.subdonic.api.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.beardbot.subsonic.client.Subsonic;
import net.beardbot.subsonic.client.base.SubsonicIncompatibilityException;
import net.iotku.subdonic.api.v1.dto.Song;
import net.iotku.subdonic.api.v1.filter.SubsonicFilter;
import net.iotku.subdonic.client.Http;
import net.iotku.subdonic.subsonic.SubsonicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@SuppressWarnings("unused")
@RequestMapping("/api/v1/subsonic")
public class SubsonicController {
    private static final Logger log = LoggerFactory.getLogger(SubsonicController.class);
    private final SubsonicConfig config;
    private final Subsonic subsonic;

    public SubsonicController(SubsonicConfig config) {
        this.config = config;
        this.subsonic = config.subsonic();
    }

    /**
     * Test endpoint to verify connection to the Subsonic server
     * @return raw string indicating the bot has a connection (or not) to the subsonic server
     */
    @GetMapping("/test")
    public String test() {
        try {
            if (subsonic.testConnection()) {
                log.info("Connected to server");
                return "Connected";
            } else {
                log.error("Failed to connect to {} with user {} and pass {}", config.getServerURL(), config.getUser(), config.getPass());
                return "Not Connected";
            }
        } catch (SubsonicIncompatibilityException e) {
            log.error("Server not compatible with client");
            return "Server Incompatible";
        }
    }

    /**
     * Subsonic API search2 method "Standard" Search
     * <a href="https://www.subsonic.org/pages/api.jsp#search2">API Reference: search2</a>
     * @param query String describing song title / artist (e.g. "two trucks lemon demon")
     * @return JSON List of songs relating to query
     */
    @GetMapping("/search2")
    @ResponseBody
    public List<Song> search2(@RequestParam String query) {
        query = query.replace(" - ", " "); // Make "artist - title" queries more reliable
        SearchParams sp = SearchParams.create();
        sp.songCount(50); // up to 10 pages of 5
        return subsonic.searching().search2(query, sp).getSongs().stream()
                .map(child -> new Song(
                        child.getTitle(),
                        child.getArtist(),
                        child.getAlbum(),
                        child.getYear() != null ? child.getYear().toString() : null,
                        child.getId()
                ))
                .filter(SubsonicFilter.taglessSong).toList();
    }

    /**
     * Subsonic API search 3 method, like search 2 but organized with ID3 tags
     * <a href="https://www.subsonic.org/pages/api.jsp#search3">API Reference: search3</a>
     * @param query String describing song title / artist (e.g. "two trucks lemon demon")
     * @return JSON List of songs relating to query
     */
    @GetMapping("/search3")
    @ResponseBody
    public List<Song> search3(@RequestParam String query) {
        query = query.replace(" - ", " "); // Make "artist - title" queries more reliable
        SearchParams sp = SearchParams.create();
        sp.songCount(50); // up to 10 pages of 5
        return subsonic.searching().search3(query, sp).getSongs().stream()
                .map(child -> new Song(
                        child.getTitle(),
                        child.getArtist(),
                        child.getAlbum(),
                        child.getYear() != null ? child.getYear().toString() : null,
                        child.getId()
                ))
                .filter(SubsonicFilter.taglessSong).toList();
    }

    /**
     * proxyStream /stream/{id} endpoint
     * @param id subsonic id from search response for distinct song
     * @return Stream of audio data provided by the subsonic server
     */
    @GetMapping("/stream/{id}")
    public ResponseEntity<InputStreamResource> proxyStream(@PathVariable String id) {
        // ! NOTE: spaces in the URL (e.g. from the subsonic client name) will DOOM YOU !
        String safeUrl = subsonic.media().stream(id).getUrl()
                .toString()
                .replace(" ", "%20");

        HttpResponse<InputStream> response;
        try {
            response = Http.makeRequestStream(safeUrl);
        } catch (IOException | InterruptedException e) {
            log.error("Failed to fetch stream for id {}", id, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not fetch stream for id " + id, e);
        }

        if (response.statusCode() != 200) {
            log.error("Subsonic returned status {} for id {}", response.statusCode(), id);
            throw new ResponseStatusException(
                    HttpStatus.valueOf(response.statusCode()),
                    "Subsonic returned status " + response.statusCode() + " for id " + id
            );
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(response.body()));
    }


    @GetMapping("/getRandomSongs")
    public List<Song> getRandomSongs(@RequestParam(defaultValue = "10") int size) throws Exception {
        String urlStr = subsonic.createUrl(
                        "getRandomSongs.view",
                        Map.of("size", List.of(String.valueOf(size)))
                ).toString()
                .replace(" ", "%20")
                .replace("&f=xml", "&f=json"); // NOTE: createUrl enforces &f=xml, so we rewrite this

        HttpResponse<String> response = Http.makeRequest(urlStr);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response.body());

        JsonNode songsNode = root
                .path("subsonic-response")
                .path("randomSongs")
                .path("song");

        Song[] songs = mapper.treeToValue(songsNode, Song[].class);
        // NOTE: Fairly unlikely to only have invalid songs, but maybe consider requesting multiple songs to be sure.
        return Arrays.stream(songs).filter(SubsonicFilter.taglessSong).toList();
    }
}
