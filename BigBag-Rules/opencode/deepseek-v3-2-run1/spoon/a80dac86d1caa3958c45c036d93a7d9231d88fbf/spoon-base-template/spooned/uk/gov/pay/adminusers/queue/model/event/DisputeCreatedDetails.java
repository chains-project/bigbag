package uk.gov.pay.adminusers.queue.model.event;
@com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class DisputeCreatedDetails {
    private java.lang.Long amount;

    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = uk.gov.service.payments.commons.api.json.ApiResponseDateTimeDeserializer.class)
    private java.time.ZonedDateTime evidenceDueDate;

    private java.lang.String gatewayAccountId;

    private java.lang.String reason;

    public DisputeCreatedDetails() {
        // empty constructor
    }

    public java.lang.Long getAmount() {
        return amount;
    }

    public java.time.ZonedDateTime getEvidenceDueDate() {
        return evidenceDueDate;
    }

    public java.lang.String getGatewayAccountId() {
        return gatewayAccountId;
    }

    public java.lang.String getReason() {
        return reason;
    }
}
