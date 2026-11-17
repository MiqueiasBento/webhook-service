package com.miqueiasbento.io.webhook_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class WebhookApiTests {
    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper mapper;

    @Test
    void managesDestinationAndDeliversEventOnce() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        HttpServer receiver = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        receiver.createContext("/webhook", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        receiver.start();
        try {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
            String url = "http://127.0.0.1:" + receiver.getAddress().getPort() + "/webhook";
            String created = mvc.perform(post("/api/v1/destinations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(new java.util.HashMap<>(java.util.Map.of("name", "Original", "url", url)))))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.active").value(true))
                    .andReturn().getResponse().getContentAsString();
            long id = mapper.readTree(created).get("id").asLong();

            mvc.perform(get("/api/v1/destinations/" + id)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Original"));
            mvc.perform(get("/api/v1/destinations")).andExpect(status().isOk());
            mvc.perform(put("/api/v1/destinations/" + id).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(java.util.Map.of("name", "Updated", "url", url))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated"));

            String delivered = mvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"destinationId\":" + id + ",\"eventType\":\"order.created\",\"payload\":{\"orderId\":7}}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DELIVERED"))
                    .andExpect(jsonPath("$.attemptCount").value(1))
                    .andReturn().getResponse().getContentAsString();
            long deliveryId = mapper.readTree(delivered).get("deliveryId").asLong();
            mvc.perform(get("/api/v1/deliveries/" + deliveryId)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DELIVERED"));
            assertThat(received.get()).contains("\"orderId\":7");

            mvc.perform(patch("/api/v1/destinations/" + id + "/status")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
            mvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"destinationId\":" + id + ",\"eventType\":\"order.created\",\"payload\":{}}"))
                    .andExpect(status().isConflict());
        } finally {
            receiver.stop(0);
        }
    }
}
