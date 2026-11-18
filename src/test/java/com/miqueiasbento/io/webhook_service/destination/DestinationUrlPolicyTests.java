package com.miqueiasbento.io.webhook_service.destination;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class DestinationUrlPolicyTests {
    private final DestinationUrlPolicy production = new DestinationUrlPolicy(false);

    @Test
    void rejectsInternalAndMalformedTargets() {
        for (String url : new String[] {
                "http://localhost:8080/hook", "http://127.0.0.1/hook",
                "http://10.0.0.1/hook", "http://100.64.0.1/hook",
                "http://[::1]/hook", "http://user:password@8.8.8.8/hook",
                "file:///etc/passwd", "https://8.8.8.8/hook#fragment"
        }) {
            assertThatThrownBy(() -> production.validate(url))
                    .as(url).isInstanceOf(ResponseStatusException.class);
        }
    }

    @Test
    void acceptsPublicIpAndAllowsLocalOnlyWhenConfigured() {
        assertThatCode(() -> production.validate("https://8.8.8.8/hook")).doesNotThrowAnyException();
        assertThatCode(() -> new DestinationUrlPolicy(true).validate("http://127.0.0.1:8090/hook"))
                .doesNotThrowAnyException();
    }
}
