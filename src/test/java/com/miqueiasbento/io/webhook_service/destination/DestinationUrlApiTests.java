package com.miqueiasbento.io.webhook_service.destination;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miqueiasbento.io.webhook_service.config.ApiKeyFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "webhook.security.allow-private-destinations=false")
class DestinationUrlApiTests {
    @Autowired WebApplicationContext context;
    @Autowired ApiKeyFilter apiKeyFilter;

    @Test
    void rejectsPrivateDestinationThroughApi() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(apiKeyFilter).build();
        mvc.perform(post("/api/v1/destinations")
                .header("X-API-Key", "test-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Local\",\"url\":\"http://127.0.0.1:8080/hook\"}"))
                .andExpect(status().isBadRequest());
    }
}
