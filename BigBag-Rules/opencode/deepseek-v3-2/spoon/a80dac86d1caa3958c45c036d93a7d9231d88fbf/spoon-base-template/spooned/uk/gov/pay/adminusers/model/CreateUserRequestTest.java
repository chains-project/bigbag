package uk.gov.pay.adminusers.model;
class CreateUserRequestTest {
    private static com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @org.junit.jupiter.api.Test
    void shouldConstructAUser_fromMinimalValidUserJson() throws java.lang.Exception {
        java.lang.String minimumUserJson = (((("{" + "\"username\": \"a-username\",") + "\"telephone_number\": \"2123524\",") + "\"gateway_account_ids\": [\"1\", \"2\"],") + "\"email\": \"email@example.com\"") + "}";
        com.fasterxml.jackson.databind.JsonNode jsonNode = uk.gov.pay.adminusers.model.CreateUserRequestTest.objectMapper.readTree(minimumUserJson);
        uk.gov.pay.adminusers.model.CreateUserRequest createUserRequest = uk.gov.pay.adminusers.model.CreateUserRequest.from(jsonNode);
        org.hamcrest.MatcherAssert.assertThat(createUserRequest.getUsername(), org.hamcrest.core.Is.is("a-username"));
        org.hamcrest.MatcherAssert.assertThat(createUserRequest.getPassword(), org.hamcrest.core.IsNull.notNullValue());
        org.hamcrest.MatcherAssert.assertThat(createUserRequest.getOtpKey(), org.hamcrest.core.IsNull.notNullValue());
        org.hamcrest.MatcherAssert.assertThat(createUserRequest.getGatewayAccountIds().size(), org.hamcrest.core.Is.is(2));
        org.hamcrest.MatcherAssert.assertThat(createUserRequest.getGatewayAccountIds().get(0), org.hamcrest.core.Is.is("1"));
        org.hamcrest.MatcherAssert.assertThat(createUserRequest.getGatewayAccountIds().get(1), org.hamcrest.core.Is.is("2"));
        org.hamcrest.MatcherAssert.assertThat(createUserRequest.getTelephoneNumber(), org.hamcrest.core.Is.is("2123524"));
        org.hamcrest.MatcherAssert.assertThat(createUserRequest.getEmail(), org.hamcrest.core.Is.is("email@example.com"));
    }

    @org.junit.jupiter.api.Test
    void shouldConstructAUser_fromCompleteValidUserJson() throws java.lang.Exception {
        java.lang.String minimunUserJson = (((((("{" + "\"username\": \"a-username\",") + "\"password\": \"a-password\",") + "\"telephone_number\": \"2123524\",") + "\"gateway_account_ids\": [\"1\", \"2\"],") + "\"otp_key\": \"fr6ysdf\",") + "\"email\": \"email@example.com\"") + "}";
        com.fasterxml.jackson.databind.JsonNode jsonNode = uk.gov.pay.adminusers.model.CreateUserRequestTest.objectMapper.readTree(minimunUserJson);
        uk.gov.pay.adminusers.model.CreateUserRequest createUserRequest = uk.gov.pay.adminusers.model.CreateUserRequest.from(jsonNode);
        org.hamcrest.MatcherAssert.assertThat(createUserRequest.getUsername(), org.hamcrest.core.Is.is("a-username"));
        org.hamcrest.MatcherAssert.assertThat(createUserRequest.getPassword(), org.hamcrest.core.Is.is("a-password"));
        org.hamcrest.MatcherAssert.assertThat(createUserRequest.getGatewayAccountIds().size(), org.hamcrest.core.Is.is(2));
        org.hamcrest.MatcherAssert.assertThat(createUserRequest.getGatewayAccountIds().get(0), org.hamcrest.core.Is.is("1"));
        org.hamcrest.MatcherAssert.assertThat(createUserRequest.getGatewayAccountIds().get(1), org.hamcrest.core.Is.is("2"));
        org.hamcrest.MatcherAssert.assertThat(createUserRequest.getTelephoneNumber(), org.hamcrest.core.Is.is("2123524"));
        org.hamcrest.MatcherAssert.assertThat(createUserRequest.getOtpKey(), org.hamcrest.core.Is.is("fr6ysdf"));
        org.hamcrest.MatcherAssert.assertThat(createUserRequest.getEmail(), org.hamcrest.core.Is.is("email@example.com"));
    }
}
