package com.coreeng.supportbot.testkit;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Calls the erasure endpoints the way Elevate's erasure orchestrator does: on the internal port, with no
 * credentials and an {@code X-Elevate-Tenant-Id} header, which Support Bot ignores.
 */
@RequiredArgsConstructor
public class ErasureRequestClient {
    private static final String PATH = "/api/erasure-requests";

    private final String internalBaseUrl;
    private final String publicBaseUrl;

    public Response submit(Map<String, ?> body) {
        return asOrchestrator().contentType(ContentType.JSON).body(body).when().post(internalBaseUrl + PATH);
    }

    public Response poll(String requestId) {
        return asOrchestrator().when().get(internalBaseUrl + PATH + "/{id}", requestId);
    }

    public Response getOnInternalPort(String path) {
        return asOrchestrator().when().get(internalBaseUrl + path);
    }

    public Response submitOnPublicPort(Map<String, ?> body) {
        return given().contentType(ContentType.JSON).body(body).when().post(publicBaseUrl + PATH);
    }

    public Response pollOnPublicPort(String requestId) {
        return given().when().get(publicBaseUrl + PATH + "/{id}", requestId);
    }

    private static RequestSpecification asOrchestrator() {
        return given().header("X-Elevate-Tenant-Id", UUID.randomUUID().toString());
    }
}
