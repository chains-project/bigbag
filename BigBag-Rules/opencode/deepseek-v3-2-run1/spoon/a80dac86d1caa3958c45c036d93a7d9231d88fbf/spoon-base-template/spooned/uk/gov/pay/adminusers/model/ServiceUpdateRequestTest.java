package uk.gov.pay.adminusers.model;
class ServiceUpdateRequestTest {
    private static com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @org.junit.jupiter.api.BeforeEach
    void before() {
        uk.gov.pay.adminusers.model.ServiceUpdateRequestTest.objectMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.AUTO_CLOSE_SOURCE, true);
    }

    @org.junit.jupiter.api.Test
    void shouldTransformToObjectCorrectly() throws java.io.IOException {
        java.util.Map<java.lang.String, java.lang.Object> payload = java.util.Map.of("path", "custom_branding", "op", "replace", "value", java.util.Map.of("image_url", "image url", "css_url", "css url"));
        java.lang.String content = uk.gov.pay.adminusers.model.ServiceUpdateRequestTest.objectMapper.writeValueAsString(payload);
        com.fasterxml.jackson.databind.JsonNode jsonNode = uk.gov.pay.adminusers.model.ServiceUpdateRequestTest.objectMapper.readTree(content);
        uk.gov.pay.adminusers.model.ServiceUpdateRequest request = uk.gov.pay.adminusers.model.ServiceUpdateRequest.from(jsonNode);
        java.util.Map<java.lang.String, java.lang.Object> objectMap = request.valueAsObject();
        org.hamcrest.MatcherAssert.assertThat(objectMap.get("image_url"), org.hamcrest.core.Is.is("image url"));
        org.hamcrest.MatcherAssert.assertThat(objectMap.get("css_url"), org.hamcrest.core.Is.is("css url"));
    }

    @org.junit.jupiter.api.Test
    void shouldReturnAList_whenJsonIsArray() throws java.io.IOException {
        java.lang.String jsonPayload = io.dropwizard.testing.FixtureHelpers.fixture("fixtures/resource/service/patch/array-replace-service-name-en-replace-service-name-cy.json");
        final java.util.List<uk.gov.pay.adminusers.model.ServiceUpdateRequest> requests = uk.gov.pay.adminusers.model.ServiceUpdateRequest.getUpdateRequests(uk.gov.pay.adminusers.model.ServiceUpdateRequestTest.objectMapper.readTree(jsonPayload));
        org.hamcrest.MatcherAssert.assertThat(requests.size(), org.hamcrest.core.Is.is(2));
        requests.sort(java.util.Comparator.comparing(uk.gov.pay.adminusers.model.ServiceUpdateRequest::getPath));
        org.hamcrest.MatcherAssert.assertThat(requests.get(0).getPath(), org.hamcrest.core.Is.is("service_name/cy"));
        org.hamcrest.MatcherAssert.assertThat(requests.get(0).getOp(), org.hamcrest.core.Is.is("replace"));
        org.hamcrest.MatcherAssert.assertThat(requests.get(1).getPath(), org.hamcrest.core.Is.is("service_name/en"));
        org.hamcrest.MatcherAssert.assertThat(requests.get(1).getOp(), org.hamcrest.core.Is.is("replace"));
    }

    @org.junit.jupiter.api.Test
    void shouldReturnAList_whenJsonIsSingleObject() throws java.io.IOException {
        // language=JSON
        java.lang.String jsonPayload = ((("{\n" + "  \"op\": \"replace\",\n") + "  \"path\": \"name\",\n") + "  \"value\": \"new-en-name\"\n") + "}\n";
        final java.util.List<uk.gov.pay.adminusers.model.ServiceUpdateRequest> requests = uk.gov.pay.adminusers.model.ServiceUpdateRequest.getUpdateRequests(uk.gov.pay.adminusers.model.ServiceUpdateRequestTest.objectMapper.readTree(jsonPayload));
        org.hamcrest.MatcherAssert.assertThat(requests.size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(requests.get(0).getPath(), org.hamcrest.core.Is.is("name"));
    }
}
