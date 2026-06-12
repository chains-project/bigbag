package uk.gov.pay.adminusers.resources;
public class EmailRequest {
    private final java.lang.String emailAddress;

    private final java.lang.String gatewayAccountId;

    private final uk.gov.pay.adminusers.resources.EmailTemplate template;

    private final java.util.Map<java.lang.String, java.lang.String> personalisation;

    public EmailRequest(java.lang.String emailAddress, java.lang.String gatewayAccountId, uk.gov.pay.adminusers.resources.EmailTemplate template, java.util.Map<java.lang.String, java.lang.String> personalisation) {
        this.emailAddress = emailAddress;
        this.gatewayAccountId = gatewayAccountId;
        this.template = template;
        this.personalisation = personalisation;
    }

    public java.lang.String getGatewayAccountId() {
        return gatewayAccountId;
    }

    public java.lang.String getEmailAddress() {
        return emailAddress;
    }

    public uk.gov.pay.adminusers.resources.EmailTemplate getTemplate() {
        return template;
    }

    public java.util.Map<java.lang.String, java.lang.String> getPersonalisation() {
        return personalisation;
    }
}
