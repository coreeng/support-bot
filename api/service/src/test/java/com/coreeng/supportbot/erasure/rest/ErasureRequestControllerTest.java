package com.coreeng.supportbot.erasure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coreeng.supportbot.erasure.ErasureRequestNotFoundException;
import com.coreeng.supportbot.erasure.ErasureRequestResponse;
import com.coreeng.supportbot.erasure.ErasureRequestService;
import com.coreeng.supportbot.erasure.ErasureRequestStatus;
import com.coreeng.supportbot.erasure.SubmitErasureRequest;
import com.coreeng.supportbot.util.JsonMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ErasureRequestControllerTest {

    private static final UUID REQUEST_ID = UUID.fromString("5f0c6a2e-2a51-4b8e-9d0a-3b7c1e4f8a10");
    private static final String CURRENT_EMAIL = "leaver@example.com";
    private static final String NEW_EMAIL = "erased@example.com";

    @Mock
    private ErasureRequestService erasureRequestService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ErasureRequestController(erasureRequestService))
                .setControllerAdvice(new ErasureRequestExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new JsonMapper().getObjectMapper()))
                .build();
    }

    @Test
    void submitAcceptsTheRequestAndReportsItsStatus() throws Exception {
        when(erasureRequestService.submit(new SubmitErasureRequest(REQUEST_ID, CURRENT_EMAIL, NEW_EMAIL)))
                .thenReturn(stored());

        mockMvc.perform(post("/api/erasure-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(REQUEST_ID.toString(), CURRENT_EMAIL, NEW_EMAIL)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID.toString()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.requestedAt").value("2026-10-15T09:30:00Z"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"currentUserEmail\":\"leaver@example.com\",\"newUserEmail\":\"erased@example.com\"}",
                "{\"requestId\":\"5f0c6a2e-2a51-4b8e-9d0a-3b7c1e4f8a10\",\"newUserEmail\":\"erased@example.com\"}",
                "{\"requestId\":\"5f0c6a2e-2a51-4b8e-9d0a-3b7c1e4f8a10\",\"currentUserEmail\":\" \","
                        + "\"newUserEmail\":\"erased@example.com\"}",
                "{\"requestId\":\"5f0c6a2e-2a51-4b8e-9d0a-3b7c1e4f8a10\",\"currentUserEmail\":\"leaver@example.com\","
                        + "\"newUserEmail\":\"not-an-email\"}",
                "{\"requestId\":\"not-a-uuid\",\"currentUserEmail\":\"leaver@example.com\","
                        + "\"newUserEmail\":\"erased@example.com\"}",
                "{\"requestId\":"
            })
    void submitRejectsAnInvalidBodyWithoutEchoingIt(String invalidBody) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/erasure-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Erasure request is invalid"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("@example.com", "not-an-email");
        verifyNoInteractions(erasureRequestService);
    }

    @Test
    void submitAcceptsABodyWithFieldsItDoesNotKnow() throws Exception {
        when(erasureRequestService.submit(new SubmitErasureRequest(REQUEST_ID, CURRENT_EMAIL, NEW_EMAIL)))
                .thenReturn(stored());

        mockMvc.perform(post("/api/erasure-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"" + REQUEST_ID + "\",\"currentUserEmail\":\"" + CURRENT_EMAIL
                                + "\",\"newUserEmail\":\"" + NEW_EMAIL + "\",\"addedByElevateLater\":true}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void submitRejectsAWellFormedEmailLongerThanTheColumn() throws Exception {
        String tooLong = "a".repeat(64) + "@" + ("b".repeat(63) + ".").repeat(3) + "example.com";
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(
                            factory
                                    .getValidator()
                                    .validateValue(SubmitErasureRequest.class, "currentUserEmail", tooLong)
                                    .stream()
                                    .map(violation -> violation
                                            .getConstraintDescriptor()
                                            .getAnnotation()
                                            .annotationType()
                                            .getName()))
                    .containsExactly(Size.class.getName());
        }

        mockMvc.perform(post("/api/erasure-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(REQUEST_ID.toString(), tooLong, NEW_EMAIL)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(erasureRequestService);
    }

    @Test
    void submittedRequestDoesNotDiscloseTheEmailsWhenLogged() {
        assertThat(new SubmitErasureRequest(REQUEST_ID, CURRENT_EMAIL, NEW_EMAIL).toString())
                .contains(REQUEST_ID.toString())
                .doesNotContain(CURRENT_EMAIL, NEW_EMAIL);
    }

    @Test
    void getReportsTheRequest() throws Exception {
        when(erasureRequestService.get(REQUEST_ID)).thenReturn(stored());

        mockMvc.perform(get("/api/erasure-requests/" + REQUEST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID.toString()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        verify(erasureRequestService).get(REQUEST_ID);
    }

    @Test
    void getAnswersNotFoundForAnUnknownId() throws Exception {
        when(erasureRequestService.get(any())).thenThrow(new ErasureRequestNotFoundException(REQUEST_ID));

        mockMvc.perform(get("/api/erasure-requests/" + REQUEST_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Erasure request not found"));
    }

    @Test
    void getRejectsAMalformedId() throws Exception {
        mockMvc.perform(get("/api/erasure-requests/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Erasure request id is invalid"));

        verifyNoInteractions(erasureRequestService);
    }

    private static ErasureRequestResponse stored() {
        return new ErasureRequestResponse(
                REQUEST_ID, ErasureRequestStatus.IN_PROGRESS, Instant.parse("2026-10-15T09:30:00Z"));
    }

    private static String body(String requestId, String currentUserEmail, String newUserEmail) {
        return "{\"requestId\":\"" + requestId + "\",\"currentUserEmail\":\"" + currentUserEmail
                + "\",\"newUserEmail\":\"" + newUserEmail + "\"}";
    }
}
