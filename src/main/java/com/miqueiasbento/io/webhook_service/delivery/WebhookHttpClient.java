package com.miqueiasbento.io.webhook_service.delivery;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import javax.net.ssl.SSLSession;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.miqueiasbento.io.webhook_service.destination.Destination;

@Component
public class WebhookHttpClient {

    private final HttpClient client;
    private final Duration requestTimeout;
    private final int maxResponseBytes;

    public WebhookHttpClient(@Value("${webhook.http.connect-timeout:5s}") Duration connectTimeout,
            @Value("${webhook.http.request-timeout:10s}") Duration requestTimeout,
            @Value("${webhook.http.max-response-bytes:65536}") int maxResponseBytes) {
        if (maxResponseBytes < 1) {
            throw new IllegalArgumentException("webhook.http.max-response-bytes must be positive");
        }
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        this.requestTimeout = requestTimeout;
        this.maxResponseBytes = maxResponseBytes;
    }

    public HttpResponse<String> send(Destination destination, String payload)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(destination.getUrl()))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            byte[] bytes = body.readNBytes(maxResponseBytes + 1);
            if (bytes.length > maxResponseBytes) {
                throw new ResponseBodyTooLargeException(response.statusCode());
            }
            return new BoundedResponse(response, new String(bytes, StandardCharsets.UTF_8));
        }
    }

    private record BoundedResponse(HttpResponse<InputStream> source, String body) implements HttpResponse<String> {
        @Override public int statusCode() { return source.statusCode(); }
        @Override public HttpRequest request() { return source.request(); }
        @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return source.headers(); }
        @Override public Optional<SSLSession> sslSession() { return source.sslSession(); }
        @Override public URI uri() { return source.uri(); }
        @Override public HttpClient.Version version() { return source.version(); }
    }
}
