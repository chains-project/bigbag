package uk.gov.pay.adminusers.model;
@com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
public class InviteCompleteRequest {
    public static final java.lang.String FIELD_GATEWAY_ACCOUNT_IDS = "gateway_account_ids";

    @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = java.lang.String.class, example = "1,2", description = "gateway_account_ids that needs to be associated for the new service. Only applicable for invite type `service`"))
    private java.util.List<java.lang.String> gatewayAccountIds = new java.util.ArrayList<>();

    public void setGatewayAccountIds(java.util.List<java.lang.String> gatewayAccountIds) {
        this.gatewayAccountIds = gatewayAccountIds;
    }

    public java.util.List<java.lang.String> getGatewayAccountIds() {
        return gatewayAccountIds;
    }
}
