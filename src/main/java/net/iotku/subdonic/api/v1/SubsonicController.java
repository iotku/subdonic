package net.iotku.subdonic.api.v1;

import net.beardbot.subsonic.client.Subsonic;
import net.beardbot.subsonic.client.base.SubsonicIncompatibilityException;
import net.iotku.subdonic.subsonic.SubsonicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.subsonic.restapi.Child;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

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
    public List<Child> search2(@RequestParam String query) {
        return subsonic.searching().search2(query).getSongs();
    }

    /**
     * Subsonic API search 3 method, like search 2 but organized with ID3 tags
     * <a href="https://www.subsonic.org/pages/api.jsp#search3">API Reference: search3</a>
     * @param query String describing song title / artist (e.g. "two trucks lemon demon")
     * @return JSON List of songs relating to query
     */
    @GetMapping("/search3")
    @ResponseBody
    public List<Child> search3(@RequestParam String query) {
        return subsonic.searching().search3(query).getSongs();
    }

    /**
     * proxyStream /stream/{id} endpoint
     * @param id subsonic id from search response for distinct song
     * @return Stream of audio data provided by the subsonic server
     * @throws IOException if an error occurs deploying the ResponseEntity
     */
    @GetMapping("/stream/{id}")
    public ResponseEntity<InputStreamResource> proxyStream(@PathVariable String id) throws IOException {
        URL url = subsonic.media().stream(id).getUrl();
        // ! NOTE: spaces in the URL will DOOM YOU !
        String safeUrl = url.toString().replace(" ", "%20");
        InputStream inputStream = new URL(safeUrl).openStream(); // TODO: use HttpClient / HttpRequest instead
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(inputStream));
    }
}
