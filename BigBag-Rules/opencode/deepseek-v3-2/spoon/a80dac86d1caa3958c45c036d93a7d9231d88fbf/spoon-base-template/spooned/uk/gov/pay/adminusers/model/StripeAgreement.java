package uk.gov.pay.adminusers.model;
public class StripeAgreement {
    public static final java.lang.String FIELD_IP_ADDRESS = "ip_address";

    @com.fasterxml.jackson.annotation.JsonProperty(uk.gov.pay.adminusers.model.StripeAgreement.FIELD_IP_ADDRESS)
    @io.swagger.v3.oas.annotations.media.Schema(example = "0.0.0.0", implementation = java.lang.String.class)
    private java.net.InetAddress ipAddress;

    @com.fasterxml.jackson.annotation.JsonProperty("agreement_time")
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = uk.gov.service.payments.commons.api.json.ApiResponseDateTimeSerializer.class)
    @io.swagger.v3.oas.annotations.media.Schema(example = "2022-04-09T16:01:56.820Z")
    private java.time.ZonedDateTime agreementTime;

    public StripeAgreement(java.net.InetAddress ipAddress, java.time.ZonedDateTime agreementTime) {
        this.ipAddress = ipAddress;
        this.agreementTime = agreementTime;
    }

    public java.net.InetAddress getIpAddress() {
        return ipAddress;
    }

    public java.time.ZonedDateTime getAgreementTime() {
        return agreementTime;
    }
}
