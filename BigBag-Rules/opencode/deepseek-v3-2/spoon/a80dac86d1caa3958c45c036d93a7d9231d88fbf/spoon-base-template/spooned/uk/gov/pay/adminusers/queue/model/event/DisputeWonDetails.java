package uk.gov.pay.adminusers.queue.model.event;
@com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class DisputeWonDetails {
    private java.lang.String gatewayAccountId;

    public DisputeWonDetails() {
        // empty constructor
    }

    public java.lang.String getGatewayAccountId() {
        return gatewayAccountId;
    }
}
