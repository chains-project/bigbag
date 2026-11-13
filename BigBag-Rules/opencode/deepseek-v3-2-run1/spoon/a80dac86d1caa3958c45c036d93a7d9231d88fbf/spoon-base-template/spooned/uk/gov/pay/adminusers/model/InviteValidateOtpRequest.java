package uk.gov.pay.adminusers.model;
@com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
public class InviteValidateOtpRequest {
    public static final java.lang.String FIELD_CODE = "code";

    public static final java.lang.String FIELD_OTP = "otp";

    private final java.lang.String code;

    private final int otpCode;

    private InviteValidateOtpRequest(java.lang.String code, int otpCode) {
        this.code = code;
        this.otpCode = otpCode;
    }

    public static uk.gov.pay.adminusers.model.InviteValidateOtpRequest from(com.fasterxml.jackson.databind.JsonNode jsonNode) {
        return new uk.gov.pay.adminusers.model.InviteValidateOtpRequest(jsonNode.get(uk.gov.pay.adminusers.model.InviteValidateOtpRequest.FIELD_CODE).asText(), jsonNode.get(uk.gov.pay.adminusers.model.InviteValidateOtpRequest.FIELD_OTP).asInt());
    }

    @io.swagger.v3.oas.annotations.media.Schema(example = "d02jddeib0lqpsir28fbskg9v0rv", required = true, maxLength = 255)
    public java.lang.String getCode() {
        return code;
    }

    @io.swagger.v3.oas.annotations.media.Schema(example = "123456", required = true)
    public int getOtpCode() {
        return otpCode;
    }
}
