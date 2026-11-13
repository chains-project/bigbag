package uk.gov.pay.adminusers.model;
public abstract class InviteRequest {
    public static final java.lang.String FIELD_EMAIL = "email";

    public static final java.lang.String FIELD_ROLE_NAME = "role_name";

    public static final java.lang.String FIELD_OTP_KEY = "otp_key";

    protected final java.lang.String roleName;

    protected final java.lang.String email;

    protected final java.lang.String otpKey;

    public InviteRequest(java.lang.String roleName, java.lang.String email, java.lang.String otpKey) {
        this.roleName = roleName;
        this.email = email;
        this.otpKey = otpKey;
    }

    @io.swagger.v3.oas.annotations.media.Schema(example = "example@example.gov.uk", required = true)
    public java.lang.String getEmail() {
        return email;
    }

    @io.swagger.v3.oas.annotations.media.Schema(example = "view-only", required = true)
    public java.lang.String getRoleName() {
        return roleName;
    }

    public java.lang.String getOtpKey() {
        return otpKey;
    }

    protected static java.lang.String getOrElseRandom(com.fasterxml.jackson.databind.JsonNode elementNode) {
        return (elementNode == null) || org.apache.commons.lang3.StringUtils.isBlank(elementNode.asText()) ? uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid() : elementNode.asText();
    }
}
