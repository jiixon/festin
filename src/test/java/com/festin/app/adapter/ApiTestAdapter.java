package com.festin.app.adapter;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.springframework.stereotype.Component;

@Component
public class ApiTestAdapter {

    private final String baseUrl;

    public ApiTestAdapter() {
        this.baseUrl = "http://localhost:8080";
        RestAssured.baseURI = baseUrl;
    }

    public Response get(String endpoint) {
        return RestAssured
                .given()
                .when()
                .get(endpoint);
    }

    public String getStatusFromHealthCheck() {
        return get("/api/health")
                .then()
                .extract()
                .path("status");
    }
}
