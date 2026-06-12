package uk.gov.pay.adminusers.resources;
public class EmailRequestParserTest {
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private uk.gov.pay.adminusers.resources.EmailRequestParser parser;

    @org.junit.jupiter.api.BeforeEach
    public void setUp() {
        parser = new uk.gov.pay.adminusers.resources.EmailRequestParser(objectMapper);
    }

    @org.junit.jupiter.api.Test
    public void shouldCreateAnEmailRequestForAValidPayload() throws uk.gov.pay.adminusers.resources.InvalidEmailRequestException {
        java.util.Map<java.lang.String, java.lang.Object> body = java.util.Map.of("address", "aaa@bbb.test", "gateway_account_external_id", "DIRECT_DEBIT:23847roidfghdkkj", "template", "MANDATE_CANCELLED", "personalisation", java.util.Map.of("field 1", "theValueOfField1", "field 2", "theValueOfField2"));
        uk.gov.pay.adminusers.resources.EmailRequest emailRequest = parser.parse(objectMapper.valueToTree(body));
        org.hamcrest.MatcherAssert.assertThat(emailRequest.getEmailAddress(), org.hamcrest.CoreMatchers.is("aaa@bbb.test"));
        org.hamcrest.MatcherAssert.assertThat(emailRequest.getGatewayAccountId(), org.hamcrest.CoreMatchers.is("DIRECT_DEBIT:23847roidfghdkkj"));
        org.hamcrest.MatcherAssert.assertThat(emailRequest.getTemplate(), org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.resources.EmailTemplate.MANDATE_CANCELLED));
        org.hamcrest.MatcherAssert.assertThat(emailRequest.getPersonalisation(), org.hamcrest.CoreMatchers.is(java.util.Map.of("field 1", "theValueOfField1", "field 2", "theValueOfField2")));
    }

    @org.junit.jupiter.api.Test
    public void shouldThrowAnExceptionForAnInvalidPayload() {
        java.util.Map<java.lang.String, java.lang.Object> body = java.util.Map.of("template", "MANDATE_CANCELLED", "personalisation", java.util.Map.of("field 1", "theValueOfField1", "field 2", "theValueOfField2"));
        uk.gov.pay.adminusers.resources.InvalidEmailRequestException exception = org.junit.jupiter.api.Assertions.assertThrows(uk.gov.pay.adminusers.resources.InvalidEmailRequestException.class, () -> parser.parse(objectMapper.valueToTree(body)));
        org.hamcrest.MatcherAssert.assertThat(exception.getMessage(), org.hamcrest.CoreMatchers.is("Error while parsing email request body"));
    }
}
