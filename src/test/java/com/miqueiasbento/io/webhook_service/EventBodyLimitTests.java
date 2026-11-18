package com.miqueiasbento.io.webhook_service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miqueiasbento.io.webhook_service.config.ApiKeyFilter;
import com.miqueiasbento.io.webhook_service.config.EventBodyLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class EventBodyLimitTests {
    @Autowired WebApplicationContext context;
    @Autowired ApiKeyFilter apiKeyFilter;
    @Autowired EventBodyLimitFilter bodyLimitFilter;

    @Test
    void rejectsBodyAboveOneMegabyteBeforeJsonParsing() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(apiKeyFilter, bodyLimitFilter).build();
        mvc.perform(post("/api/v1/events")
                .header("X-API-Key", "test-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new byte[1048577]))
                .andExpect(status().isPayloadTooLarge());
    }
}
