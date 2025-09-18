package net.iotku.subdonic.subsonic;

import net.beardbot.subsonic.client.Subsonic;
import net.beardbot.subsonic.client.SubsonicPreferences;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SubsonicConfig {
    @Value("${subsonic.url}")
    private String serverURL;

    @Value("${subsonic.user}")
    private String user;

    @Value("${subsonic.pass}")
    private String pass;

    @Bean
    public Subsonic subsonic() {
        SubsonicPreferences preferences = new SubsonicPreferences(serverURL, user, pass);
        preferences.setStreamBitRate(320); // TODO: Investigate possible formats
        preferences.setClientName("Subdonic Discord Bot");
        return new Subsonic(preferences);
    }

    public String getServerURL() {
        return serverURL;
    }

    public String getPass() {
        return pass;
    }

    public String getUser() {
        return user;
    }
}
