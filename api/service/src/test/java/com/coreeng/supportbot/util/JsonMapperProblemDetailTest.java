package com.coreeng.supportbot.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * {@link JsonMapper} replaces Spring Boot's ObjectMapper for the whole HTTP layer, so the
 * ProblemDetail bodies returned by the exception handlers are shaped by it, not by Spring's
 * defaults. The MockMvc slices do not load it, which is how a nested "properties" object once
 * reached production unnoticed.
 */
class JsonMapperProblemDetailTest {

    @Test
    void serialisesProblemDetailExtensionsAtTopLevel() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "to must not be before from");
        problem.setTitle("Invalid summary window");
        problem.setProperty("code", "SUMMARY_WINDOW_INVALID");

        String json = new JsonMapper().toJsonString(problem);
        // RFC 9457 shape: extension members sit beside the standard ones, and unset members are
        // omitted rather than written as null.
        assertThat(json)
                .contains("\"code\":\"SUMMARY_WINDOW_INVALID\"")
                .contains("\"status\":400")
                .contains("\"title\":\"Invalid summary window\"")
                .doesNotContain("\"properties\"")
                .doesNotContain("\"instance\"");
    }
}
