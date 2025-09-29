package net.iotku.subdonic.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Http {
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    public static HttpResponse<String> makeRequest(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    public static HttpResponse<InputStream> makeRequestStream(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
    }
}
