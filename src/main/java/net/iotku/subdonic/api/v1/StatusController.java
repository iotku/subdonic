package net.iotku.subdonic.api.v1;

import discord4j.core.object.entity.Guild;
import org.springframework.web.bind.annotation.*;
import net.iotku.subdonic.api.v1.dto.DiscordGuild;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/status")
public class StatusController {
    List<DiscordGuild> activeServers = new ArrayList<>();

    /**
     * Add server to activeServer list
     * @return true if successful
     */
    @PutMapping("/guild/add")
    public boolean add(@RequestBody DiscordGuild guildDTO) {
        System.out.println("Added guild in status controller: " + guildDTO);
        if (guildDTO == null) {
            System.out.println("JSON deserialization failed.");
            return false;
        }

        return activeServers.add(guildDTO);
    }


    @GetMapping("/guild/list")
    public List<DiscordGuild> list() {
        return activeServers;
    }

}
