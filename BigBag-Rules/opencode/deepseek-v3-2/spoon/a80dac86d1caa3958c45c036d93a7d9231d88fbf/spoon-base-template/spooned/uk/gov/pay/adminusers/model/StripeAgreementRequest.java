package uk.gov.pay.adminusers.model;
public class StripeAgreementRequest {
    @javax.validation.constraints.NotNull
    @uk.gov.pay.adminusers.resources.ValidIpAddress
    @io.swagger.v3.oas.annotations.media.Schema(name = "ip_address", example = "0.0.0.0", required = true)
    private final java.lang.String ipAddress;

    @com.fasterxml.jackson.annotation.JsonCreator
    public StripeAgreementRequest(@com.fasterxml.jackson.annotation.JsonProperty("ip_address")
    java.lang.String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public java.lang.String getIpAddress() {
        return ipAddress;
    }

    @java.lang.Override
    public java.lang.String toString() {
        return ((("StripeAgreementRequest{" + "ipAddress='") + ipAddress) + '\'') + '}';
    }
}
