package net.iotku.subdonic.ApiClient;

import discord4j.core.object.entity.Guild;

import java.io.IOException;
import java.net.http.HttpResponse;

public class Status {
    public static HttpResponse<String> addGuild(Guild guild) throws IOException, InterruptedException {
        String url = Http.baseUrl + "status/guild/add";

        // TODO: Properly serialize json
        String json = "{"
                + "\"id\":\"" + guild.getId().asString() + "\","
                + "\"name\":\"" + guild.getName() + "\","
                + "\"memberCount\":" + guild.getMemberCount()
                + "}";

        HttpResponse<String> resp = Http.makePutRequest(url, json);

        if (resp.statusCode() >= 400) {
            System.err.println("Failed to add guild! Server returned: "
                    + resp.statusCode() + " " + resp.body());
        }

        return resp;
    }
}
