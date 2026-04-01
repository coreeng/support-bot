package com.coreeng.supportbot.prtracking.rest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.coreeng.supportbot.prtracking.EscalationBreakdown;
import com.coreeng.supportbot.prtracking.PrTrackingRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TenantInsightsRouteRegistrationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    PropertyPlaceholderAutoConfiguration.class,
                    JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class,
                    WebMvcAutoConfiguration.class,
                    DispatcherServletAutoConfiguration.class))
            .withUserConfiguration(TenantInsightsTestConfig.class);

    @Test
    void enabledEndpointRemainsAvailable_whenPrTrackingDisabled() {
        contextRunner.withPropertyValues("pr-review-tracking.enabled=false").run(context -> {
            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

            mockMvc.perform(get("/tenant-insights/enabled"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("{\"enabled\":false}"));

            mockMvc.perform(get("/tenant-insights/pr-stats")).andExpect(status().isNotFound());
            mockMvc.perform(get("/tenant-insights/escalation-breakdown")).andExpect(status().isNotFound());
        });
    }

    @Test
    void dataEndpointsAreRegistered_whenPrTrackingEnabled() {
        contextRunner
                .withPropertyValues(
                        "pr-review-tracking.enabled=true",
                        "pr-review-tracking.poll-cron=0 0 9-18 * * 1-5",
                        "pr-review-tracking.pr-emoji=pr",
                        "pr-review-tracking.tags[0]=tag",
                        "pr-review-tracking.impact=Information Request",
                        "pr-review-tracking.duration-unit=hours",
                        "pr-review-tracking.repositories[0].name=my-org/onboarding-repo",
                        "pr-review-tracking.repositories[0].owning-team=wow",
                        "pr-review-tracking.repositories[0].sla.default=PT48H",
                        "pr-review-tracking.github.auth-mode=token",
                        "pr-review-tracking.github.api-base-url=https://api.github.com",
                        "pr-review-tracking.github.token=pat-123")
                .run(context -> {
                    PrTrackingRepository repository = context.getBean(PrTrackingRepository.class);
                    when(repository.getInsightsByRepo(null, null)).thenReturn(List.of());
                    when(repository.getEscalationBreakdown(null, null)).thenReturn(new EscalationBreakdown(0, 0, 0));

                    MockMvc mockMvc =
                            MockMvcBuilders.webAppContextSetup(context).build();

                    mockMvc.perform(get("/tenant-insights/enabled"))
                            .andExpect(status().isOk())
                            .andExpect(content().json("{\"enabled\":true}"));
                    mockMvc.perform(get("/tenant-insights/pr-stats"))
                            .andExpect(status().isOk())
                            .andExpect(content().json("[]"));
                    mockMvc.perform(get("/tenant-insights/escalation-breakdown"))
                            .andExpect(status().isOk());
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PrTrackingProps.class)
    @Import({TenantInsightsEnabledController.class, TenantInsightsController.class})
    static class TenantInsightsTestConfig {

        @Bean
        PrTrackingRepository prTrackingRepository() {
            return mock(PrTrackingRepository.class);
        }
    }
}
