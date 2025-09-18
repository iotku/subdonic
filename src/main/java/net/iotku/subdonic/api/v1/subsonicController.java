package net.iotku.subdonic.api.v1;

import net.beardbot.subsonic.client.Subsonic;
import net.beardbot.subsonic.client.base.SubsonicIncompatibilityException;
import net.iotku.subdonic.subsonic.SubsonicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subsonic")
public class subsonicController {
    private static final Logger log = LoggerFactory.getLogger(subsonicController.class);
    private final SubsonicConfig config;
    private final Subsonic subsonic;

    public subsonicController(SubsonicConfig config) {
        this.config = config;
        this.subsonic = config.subsonic();
    }

    @GetMapping("/ping")
    public String ping() {
        try {
            if (subsonic.testConnection()) {
                log.info("Connected to server");
            } else {
                log.error("Failed to connect to {} with user {} and pass {}", config.getServerURL(), config.getUser(), config.getPass());
            }
        } catch (SubsonicIncompatibilityException e) {
            log.error("Server not compatible with client");
        }
        return "";
    }
}
