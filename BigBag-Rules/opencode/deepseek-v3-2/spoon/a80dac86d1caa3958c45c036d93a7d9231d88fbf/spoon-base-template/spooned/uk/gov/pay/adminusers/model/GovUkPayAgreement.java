package uk.gov.pay.adminusers.model;
@com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GovUkPayAgreement {
    @io.swagger.v3.oas.annotations.media.Schema(example = "someone@somedepartment.gov.uk")
    private java.lang.String email;

    @io.swagger.v3.oas.annotations.media.Schema(example = "2022-04-10T16:23:35.684Z")
    private java.time.ZonedDateTime agreementTime;

    public GovUkPayAgreement(java.lang.String email, java.time.ZonedDateTime agreementTime) {
        this.email = email;
        this.agreementTime = agreementTime;
    }

    public java.lang.String getEmail() {
        return email;
    }

    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = uk.gov.service.payments.commons.api.json.ApiResponseDateTimeSerializer.class)
    public java.time.ZonedDateTime getAgreementTime() {
        return agreementTime;
    }
}
