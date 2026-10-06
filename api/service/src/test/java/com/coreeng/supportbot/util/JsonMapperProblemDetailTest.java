package com.coreeng.supportbot.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.coreeng.supportbot.config.UtilsConfig;
import com.coreeng.supportbot.stats.StatsRequest;
import com.coreeng.supportbot.ticket.TicketsQuery;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

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

    @Test
    void preservesJacksonizedBuildersGuavaCollectionsAndStringDates() {
        var mapper = new JsonMapper();
        var query = mapper.fromJsonString(
                "{\"dateFrom\":\"2026-03-10\",\"tags\":[\"bug\"],\"order\":\"asc\"}", TicketsQuery.class);
        assertThat(query.pageSize()).isEqualTo(10);
        assertThat(query.dateFrom()).isEqualTo(LocalDate.of(2026, 3, 10));
        assertThat(query.tags()).containsExactly("bug");
        assertThat(query.order()).isEqualTo(TicketsQuery.Order.asc);
        assertThat(mapper.toJsonString(query)).contains("\"dateFrom\":\"2026-03-10\"", "\"tags\":[\"bug\"]");
        assertThat(mapper.fromJsonString("{\"type\":\"ticket-timeline\",\"metric\":\"opened\"}", StatsRequest.class))
                .isInstanceOf(StatsRequest.TicketTimeline.class);
    }

    @Test
    void bootRestClientUsesTheApplicationMapper() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        JacksonAutoConfiguration.class,
                        HttpMessageConvertersAutoConfiguration.class,
                        RestClientAutoConfiguration.class))
                .withUserConfiguration(UtilsConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(tools.jackson.databind.json.JsonMapper.class);
                    var mapper = context.getBean(tools.jackson.databind.json.JsonMapper.class);
                    assertThat(mapper)
                            .isSameAs(context.getBean(JsonMapper.class).getObjectMapper());
                    var verified = new AtomicBoolean();
                    var builder = context.getBean(RestClient.Builder.class)
                            .configureMessageConverters(c -> c.configureMessageConverters(converter -> {
                                if (converter instanceof JacksonJsonHttpMessageConverter json) {
                                    assertThat(json.getMapper()).isSameAs(mapper);
                                    verified.set(true);
                                }
                            }));
                    var server = MockRestServiceServer.bindTo(builder).build();
                    server.expect(requestTo("https://example.test/query"))
                            .andRespond(withSuccess(
                                    "{\"dateFrom\":\"2026-03-10\",\"tags\":[\"bug\"]}", MediaType.APPLICATION_JSON));
                    var query = builder.build()
                            .get()
                            .uri("https://example.test/query")
                            .retrieve()
                            .body(TicketsQuery.class);
                    assertThat(query).isNotNull();
                    assertThat(query.tags()).containsExactly("bug");
                    assertThat(verified).isTrue();
                    server.verify();
                });
    }
}
