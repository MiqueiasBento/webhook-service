package com.miqueiasbento.io.webhook_service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miqueiasbento.io.webhook_service.config.ApiKeyFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class ApiKeyFilterTests {
    @Autowired WebApplicationContext context;
    @Autowired ApiKeyFilter filter;

    @Test
    void protectsEveryBusinessRoute() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(filter).build();
        mvc.perform(get("/api/v1/destinations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/destinations/1").header("X-API-Key", "wrong"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/events")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/deliveries/1")).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/destinations").header("X-API-Key", "test-api-key"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/deliveries/9223372036854775807")
                .header("X-API-Key", "test-api-key")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/events").header("X-API-Key", "test-api-key"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
}
