package uk.gov.pay.adminusers.queue.model.event;
@com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class DisputeLostDetails {
    private java.lang.Long netAmount;

    private java.lang.Long amount;

    private java.lang.Long fee;

    private java.lang.String gatewayAccountId;

    public DisputeLostDetails() {
        // empty constructor
    }

    public java.lang.Long getNetAmount() {
        return netAmount;
    }

    public java.lang.Long getAmount() {
        return amount;
    }

    public java.lang.Long getFee() {
        return fee;
    }

    public java.lang.String getGatewayAccountId() {
        return gatewayAccountId;
    }
}
