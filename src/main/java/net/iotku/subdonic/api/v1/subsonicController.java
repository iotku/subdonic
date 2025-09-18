package net.iotku.subdonic.api.v1;

import net.beardbot.subsonic.client.Subsonic;
import net.beardbot.subsonic.client.base.SubsonicIncompatibilityException;
import net.iotku.subdonic.subsonic.SubsonicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.subsonic.restapi.Child;

import java.util.List;

@RestController
@SuppressWarnings("unused")
@RequestMapping("/api/v1/subsonic")
public class subsonicController {
    private static final Logger log = LoggerFactory.getLogger(subsonicController.class);
    private final SubsonicConfig config;
    private final Subsonic subsonic;

    public subsonicController(SubsonicConfig config) {
        this.config = config;
        this.subsonic = config.subsonic();
    }

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

    @GetMapping("/search2")
    @ResponseBody
    public List<Child> search2(@RequestParam String query) {
        return subsonic.searching().search2(query).getSongs();
    }

    @GetMapping("/search3")
    @ResponseBody
    public List<Child> search3(@RequestParam String query) {
        return subsonic.searching().search3(query).getSongs();
    }
}
