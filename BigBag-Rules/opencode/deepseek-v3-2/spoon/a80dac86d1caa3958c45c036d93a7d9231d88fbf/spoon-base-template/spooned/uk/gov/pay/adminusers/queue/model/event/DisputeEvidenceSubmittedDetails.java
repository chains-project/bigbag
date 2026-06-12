package uk.gov.pay.adminusers.queue.model.event;
@com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class DisputeEvidenceSubmittedDetails {
    private java.lang.String gatewayAccountId;

    public DisputeEvidenceSubmittedDetails() {
        // empty constructor
    }

    public java.lang.String getGatewayAccountId() {
        return gatewayAccountId;
    }
}
