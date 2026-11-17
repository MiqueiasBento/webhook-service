package com.miqueiasbento.io.webhook_service.delivery;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.miqueiasbento.io.webhook_service.destination.Destination;

@Component
public class WebhookHttpClient {

    private final HttpClient client;
    private final Duration requestTimeout;

    public WebhookHttpClient(@Value("${webhook.http.connect-timeout:5s}") Duration connectTimeout,
            @Value("${webhook.http.request-timeout:10s}") Duration requestTimeout) {
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        this.requestTimeout = requestTimeout;
    }

    public HttpResponse<String> send(Destination destination, String payload)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(destination.getUrl()))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
