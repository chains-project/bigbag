package uk.gov.pay.adminusers.model;
public class ServiceSearchRequest {
    private static final java.lang.String FIELD_SERVICE_NAME = "service_name";

    private static final java.lang.String FIELD_SERVICE_MERCHANT_NAME = "service_merchant_name";

    private final java.lang.String serviceNameSearchString;

    private final java.lang.String serviceMerchantNameSearchString;

    public ServiceSearchRequest(java.lang.String serviceName, java.lang.String serviceMerchantName) {
        this.serviceNameSearchString = serviceName;
        this.serviceMerchantNameSearchString = serviceMerchantName;
    }

    public static uk.gov.pay.adminusers.model.ServiceSearchRequest from(com.fasterxml.jackson.databind.JsonNode payload) {
        java.lang.String serviceName = java.util.Optional.ofNullable(payload.get(uk.gov.pay.adminusers.model.ServiceSearchRequest.FIELD_SERVICE_NAME)).map(com.fasterxml.jackson.databind.JsonNode::asText).orElse("");
        java.lang.String serviceOrg = java.util.Optional.ofNullable(payload.get(uk.gov.pay.adminusers.model.ServiceSearchRequest.FIELD_SERVICE_MERCHANT_NAME)).map(com.fasterxml.jackson.databind.JsonNode::asText).orElse("");
        return new uk.gov.pay.adminusers.model.ServiceSearchRequest(serviceName, serviceOrg);
    }

    public java.lang.String getServiceNameSearchString() {
        return serviceNameSearchString;
    }

    public java.lang.String getServiceMerchantNameSearchString() {
        return serviceMerchantNameSearchString;
    }

    public java.util.Map<java.lang.String, java.lang.String> toMap() {
        return java.util.Map.of(uk.gov.pay.adminusers.model.ServiceSearchRequest.FIELD_SERVICE_NAME, serviceNameSearchString, uk.gov.pay.adminusers.model.ServiceSearchRequest.FIELD_SERVICE_MERCHANT_NAME, serviceMerchantNameSearchString);
    }
}
