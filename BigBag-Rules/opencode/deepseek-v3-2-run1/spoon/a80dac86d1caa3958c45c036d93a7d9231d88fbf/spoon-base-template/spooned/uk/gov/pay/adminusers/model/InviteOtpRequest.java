package uk.gov.pay.adminusers.model;
@com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
public class InviteOtpRequest {
    public static final java.lang.String FIELD_CODE = "code";

    public static final java.lang.String FIELD_TELEPHONE_NUMBER = "telephone_number";

    public static final java.lang.String FIELD_PASSWORD = "password";

    // until "/otp/resend" is refactored into "{code}/otp/resend"
    @java.lang.Deprecated
    private java.lang.String code;

    private java.lang.String telephoneNumber;

    private java.lang.String password;

    private InviteOtpRequest() {
    }

    private InviteOtpRequest(java.lang.String code, java.lang.String telephoneNumber, java.lang.String password) {
        this.code = code;
        this.telephoneNumber = telephoneNumber;
        this.password = password;
    }

    public static uk.gov.pay.adminusers.model.InviteOtpRequest from(com.fasterxml.jackson.databind.JsonNode jsonNode) {
        if ((jsonNode == null) || (!jsonNode.fieldNames().hasNext())) {
            return new uk.gov.pay.adminusers.model.InviteOtpRequest();
        } else {
            java.lang.String password = java.util.Optional.ofNullable(jsonNode.get(uk.gov.pay.adminusers.model.InviteOtpRequest.FIELD_PASSWORD)).map(com.fasterxml.jackson.databind.JsonNode::asText).orElse(null);
            java.lang.String code = java.util.Optional.ofNullable(jsonNode.get(uk.gov.pay.adminusers.model.InviteOtpRequest.FIELD_CODE)).map(com.fasterxml.jackson.databind.JsonNode::asText).orElse(null);
            return new uk.gov.pay.adminusers.model.InviteOtpRequest(code, jsonNode.get(uk.gov.pay.adminusers.model.InviteOtpRequest.FIELD_TELEPHONE_NUMBER).asText(), password);
        }
    }

    @java.lang.Deprecated
    @io.swagger.v3.oas.annotations.media.Schema(example = "d02jddeib0lqpsir28fbskg9v0rv", maxLength = 255, required = true)
    public java.lang.String getCode() {
        return code;
    }

    @io.swagger.v3.oas.annotations.media.Schema(example = "440787654534", required = true)
    public java.lang.String getTelephoneNumber() {
        return telephoneNumber;
    }

    @io.swagger.v3.oas.annotations.media.Schema(example = "a-password")
    public java.lang.String getPassword() {
        return password;
    }
}
