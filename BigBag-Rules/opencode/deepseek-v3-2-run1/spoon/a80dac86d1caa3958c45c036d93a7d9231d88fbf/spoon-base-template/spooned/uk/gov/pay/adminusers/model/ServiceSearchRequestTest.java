package uk.gov.pay.adminusers.model;
class ServiceSearchRequestTest {
    private static final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @org.junit.jupiter.api.Test
    void shouldDeserialiseCorrectly() throws com.fasterxml.jackson.core.JsonProcessingException {
        var searchRequest = uk.gov.pay.adminusers.model.ServiceSearchRequest.from(uk.gov.pay.adminusers.model.ServiceSearchRequestTest.mapper.readTree("{\"service_name\": \"serv name\", \"service_merchant_name\": \"merchant name\"}"));
        org.hamcrest.MatcherAssert.assertThat(searchRequest.getServiceNameSearchString(), org.hamcrest.core.Is.is("serv name"));
        org.hamcrest.MatcherAssert.assertThat(searchRequest.getServiceMerchantNameSearchString(), org.hamcrest.core.Is.is("merchant name"));
    }

    @org.junit.jupiter.api.Test
    void shouldSetDefaultEmptyStrings_onMalformedJSON() throws com.fasterxml.jackson.core.JsonProcessingException {
        var searchRequest = uk.gov.pay.adminusers.model.ServiceSearchRequest.from(uk.gov.pay.adminusers.model.ServiceSearchRequestTest.mapper.readTree("{\"random_key\": \"random val\", \"random_key2\": \"random val\"}"));
        org.hamcrest.MatcherAssert.assertThat(searchRequest.getServiceNameSearchString(), org.hamcrest.core.Is.is(""));
        org.hamcrest.MatcherAssert.assertThat(searchRequest.getServiceMerchantNameSearchString(), org.hamcrest.core.Is.is(""));
    }

    @org.junit.jupiter.api.Test
    void shouldReturnMapOfValues() throws com.fasterxml.jackson.core.JsonProcessingException {
        var searchRequest = uk.gov.pay.adminusers.model.ServiceSearchRequest.from(uk.gov.pay.adminusers.model.ServiceSearchRequestTest.mapper.readTree("{\"service_name\": \"serv name\", \"service_merchant_name\": \"merchant name\"}"));
        var mapOfRequest = searchRequest.toMap();
        mapOfRequest.keySet().forEach(key -> org.hamcrest.MatcherAssert.assertThat(mapOfRequest.get(key), org.hamcrest.Matchers.anyOf(org.hamcrest.Matchers.equalTo("serv name"), org.hamcrest.Matchers.equalTo("merchant name"))));
    }
}
