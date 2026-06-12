package uk.gov.pay.adminusers.model;
public class InviteServiceRequest extends uk.gov.pay.adminusers.model.InviteRequest {
    public static final java.lang.String FIELD_PASSWORD = "password";

    public static final java.lang.String FIELD_TELEPHONE_NUMBER = "telephone_number";

    private static final java.lang.String DEFAULT_ROLE_NAME = "admin";

    private java.lang.String password;

    private java.lang.String telephoneNumber;

    private InviteServiceRequest(java.lang.String roleName, java.lang.String password, java.lang.String email, java.lang.String telephoneNumber, java.lang.String otpKey) {
        super(roleName, email, otpKey);
        this.password = password;
        this.telephoneNumber = telephoneNumber;
    }

    public InviteServiceRequest(java.lang.String password, java.lang.String email, java.lang.String telephoneNumber) {
        this(uk.gov.pay.adminusers.model.InviteServiceRequest.DEFAULT_ROLE_NAME, password, email, telephoneNumber, uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid());
    }

    public InviteServiceRequest(java.lang.String email) {
        this(uk.gov.pay.adminusers.model.InviteServiceRequest.DEFAULT_ROLE_NAME, null, email, null, uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid());
    }

    public java.lang.String getPassword() {
        return password;
    }

    public void setPassword(java.lang.String password) {
        this.password = password;
    }

    public java.lang.String getTelephoneNumber() {
        return telephoneNumber;
    }

    public void setTelephoneNumber(java.lang.String telephoneNumber) {
        this.telephoneNumber = telephoneNumber;
    }

    public static uk.gov.pay.adminusers.model.InviteServiceRequest from(com.fasterxml.jackson.databind.JsonNode payload) {
        return new uk.gov.pay.adminusers.model.InviteServiceRequest(uk.gov.pay.adminusers.model.InviteServiceRequest.DEFAULT_ROLE_NAME, java.util.Optional.ofNullable(payload.get(uk.gov.pay.adminusers.model.InviteServiceRequest.FIELD_PASSWORD)).map(com.fasterxml.jackson.databind.JsonNode::asText).orElse(null), payload.get(uk.gov.pay.adminusers.model.InviteRequest.FIELD_EMAIL).asText(), java.util.Optional.ofNullable(payload.get(uk.gov.pay.adminusers.model.InviteServiceRequest.FIELD_TELEPHONE_NUMBER)).map(com.fasterxml.jackson.databind.JsonNode::asText).orElse(null), uk.gov.pay.adminusers.model.InviteRequest.getOrElseRandom(payload.get(uk.gov.pay.adminusers.model.InviteRequest.FIELD_OTP_KEY)));
    }
}
