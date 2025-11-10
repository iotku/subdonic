package net.iotku.subdonic.api.v1;

import org.springframework.web.bind.annotation.*;
import net.iotku.subdonic.api.v1.dto.DiscordGuild;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/status")
public class StatusController {
    Set<DiscordGuild> activeServers = ConcurrentHashMap.newKeySet();

    /**
     * Add server to activeServer list
     * @return true if successful
     */
    @PutMapping("/guild/add")
    public boolean add(@RequestBody DiscordGuild guildDTO) {
        if (guildDTO == null) {
            System.out.println("JSON deserialization failed.");
            return false;
        }

        return activeServers.add(guildDTO);
    }

    // TODO: /guild/remove


    /**
     * List 'active' DiscordGuild(s)
     * @return JSON list of DiscordGuild
     */
    @GetMapping("/guild/list")
    public Set<DiscordGuild> list() {
        return activeServers;
    }

}
