package net.iotku.subdonic.ApiClient;

import discord4j.core.object.entity.Guild;
import net.iotku.subdonic.api.v1.dto.DiscordGuild;

import java.io.IOException;
import java.net.http.HttpResponse;

public class Status {
    public static HttpResponse<String> addGuild(Guild guild) throws IOException, InterruptedException {
        String url = Http.baseUrl + "status/guild/add";

        String json = Http.MAPPER.writeValueAsString(DiscordGuild.fromNativeGuild(guild));
        HttpResponse<String> resp = Http.makePutRequest(url, json);

        if (resp.statusCode() >= 400) {
            System.err.println("Failed to add guild! Server returned: "
                    + resp.statusCode() + " " + resp.body());
        }

        return resp;
    }
}
