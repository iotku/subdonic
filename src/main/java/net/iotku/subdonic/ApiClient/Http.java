package net.iotku.subdonic.ApiClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Http {
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    public static final String baseUrl = "http://localhost:8080/api/v1/";
    public static final ObjectMapper MAPPER = new ObjectMapper();

    public static HttpResponse<String> makeGetRequest(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    public static HttpResponse<InputStream> makeGetRequestStream(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
    }

    public static HttpResponse<String> makePutRequest(String url, String data)
            throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(data))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        return resp;
    }
}
