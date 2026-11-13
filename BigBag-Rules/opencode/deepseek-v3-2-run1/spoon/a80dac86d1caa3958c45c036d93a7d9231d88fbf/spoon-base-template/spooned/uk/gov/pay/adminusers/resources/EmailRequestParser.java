package uk.gov.pay.adminusers.resources;
public class EmailRequestParser {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(uk.gov.pay.adminusers.resources.EmailRequestParser.class);

    private com.fasterxml.jackson.databind.ObjectMapper mapper;

    @com.google.inject.Inject
    public EmailRequestParser(com.fasterxml.jackson.databind.ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /* default */
    uk.gov.pay.adminusers.resources.EmailRequest parse(com.fasterxml.jackson.databind.JsonNode payload) throws uk.gov.pay.adminusers.resources.InvalidEmailRequestException {
        try {
            java.lang.String emailAddress = payload.get("address").asText();
            java.lang.String gatewayAccountId = payload.get("gateway_account_external_id").asText();
            uk.gov.pay.adminusers.resources.EmailTemplate template = uk.gov.pay.adminusers.resources.EmailTemplate.fromString(payload.get("template").asText());
            java.util.Map<java.lang.String, java.lang.String> personalisation = mapper.convertValue(payload.get("personalisation"), java.util.Map.class);
            return new uk.gov.pay.adminusers.resources.EmailRequest(emailAddress, gatewayAccountId, template, personalisation);
        } catch (java.lang.Exception exc) {
            uk.gov.pay.adminusers.resources.EmailRequestParser.LOGGER.error("Error while parsing email request, exception: {}", exc.getMessage());
            throw new uk.gov.pay.adminusers.resources.InvalidEmailRequestException("Error while parsing email request body", exc);
        }
    }
}
