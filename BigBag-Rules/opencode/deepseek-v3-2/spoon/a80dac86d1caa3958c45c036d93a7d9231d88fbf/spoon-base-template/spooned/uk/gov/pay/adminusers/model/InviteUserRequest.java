package uk.gov.pay.adminusers.model;
@com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
public class InviteUserRequest extends uk.gov.pay.adminusers.model.InviteRequest {
    public static final java.lang.String FIELD_SENDER = "sender";

    public static final java.lang.String FIELD_SERVICE_EXTERNAL_ID = "service_external_id";

    private final java.lang.String sender;

    private java.lang.String serviceExternalId;

    private InviteUserRequest(java.lang.String sender, java.lang.String email, java.lang.String roleName, java.lang.String otpKey, java.lang.String serviceExternalId) {
        super(roleName, email, otpKey);
        this.sender = sender;
        this.serviceExternalId = serviceExternalId;
    }

    public static uk.gov.pay.adminusers.model.InviteUserRequest from(com.fasterxml.jackson.databind.JsonNode jsonNode) {
        return new uk.gov.pay.adminusers.model.InviteUserRequest(jsonNode.get(uk.gov.pay.adminusers.model.InviteUserRequest.FIELD_SENDER).asText(), jsonNode.get(uk.gov.pay.adminusers.model.InviteRequest.FIELD_EMAIL).asText(), jsonNode.get(uk.gov.pay.adminusers.model.InviteRequest.FIELD_ROLE_NAME).asText(), uk.gov.pay.adminusers.model.InviteRequest.getOrElseRandom(jsonNode.get(uk.gov.pay.adminusers.model.InviteRequest.FIELD_OTP_KEY)), jsonNode.get(uk.gov.pay.adminusers.model.InviteUserRequest.FIELD_SERVICE_EXTERNAL_ID).asText());
    }

    @java.lang.Deprecated
    public static uk.gov.pay.adminusers.model.InviteUserRequest from(com.fasterxml.jackson.databind.JsonNode jsonNode, java.lang.String serviceExternalId) {
        return new uk.gov.pay.adminusers.model.InviteUserRequest(jsonNode.get(uk.gov.pay.adminusers.model.InviteUserRequest.FIELD_SENDER).asText(), jsonNode.get(uk.gov.pay.adminusers.model.InviteRequest.FIELD_EMAIL).asText(), jsonNode.get(uk.gov.pay.adminusers.model.InviteRequest.FIELD_ROLE_NAME).asText(), uk.gov.pay.adminusers.model.InviteRequest.getOrElseRandom(jsonNode.get(uk.gov.pay.adminusers.model.InviteRequest.FIELD_OTP_KEY)), serviceExternalId);
    }

    @io.swagger.v3.oas.annotations.media.Schema(example = "d0wksn12nklsdf1nd02nd9n2ndk", description = "User external ID", required = true)
    public java.lang.String getSender() {
        return sender;
    }

    @io.swagger.v3.oas.annotations.media.Schema(example = "dj2jkejke32jfhh3", required = true)
    public java.lang.String getServiceExternalId() {
        return serviceExternalId;
    }
}
