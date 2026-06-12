package uk.gov.pay.adminusers.resources;
// TODO remove this resource as it's only applicable for sending direct debit emails
@javax.ws.rs.Path("/")
public class EmailResource {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(uk.gov.pay.adminusers.resources.EmailResource.class);

    private final uk.gov.pay.adminusers.service.EmailService notificationService;

    private final uk.gov.pay.adminusers.resources.EmailRequestParser emailRequestParser;

    @com.google.inject.Inject
    public EmailResource(uk.gov.pay.adminusers.service.EmailService notificationService, uk.gov.pay.adminusers.resources.EmailRequestParser emailRequestParser) {
        this.notificationService = notificationService;
        this.emailRequestParser = emailRequestParser;
    }

    @javax.ws.rs.Path("/v1/emails/send")
    @javax.ws.rs.POST
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    public javax.ws.rs.core.Response sendEmail(com.fasterxml.jackson.databind.JsonNode payload) throws uk.gov.pay.adminusers.resources.InvalidEmailRequestException, uk.gov.pay.adminusers.resources.InvalidMerchantDetailsException {
        uk.gov.pay.adminusers.resources.EmailResource.LOGGER.info("Received email request");
        uk.gov.pay.adminusers.resources.EmailRequest emailRequest = emailRequestParser.parse(payload);
        uk.gov.pay.adminusers.resources.EmailTemplate template = emailRequest.getTemplate();
        java.lang.String gatewayAccountId = emailRequest.getGatewayAccountId();
        uk.gov.pay.adminusers.resources.EmailResource.LOGGER.info("Sending {} email for account {}", template, gatewayAccountId);
        notificationService.sendEmail(emailRequest.getEmailAddress(), gatewayAccountId, template, emailRequest.getPersonalisation());
        return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.OK).build();
    }
}
