package uk.gov.pay.adminusers.model;
@com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy.class)
public class CreateUserRequest {
    public static final java.lang.String FIELD_USERNAME = "username";

    public static final java.lang.String FIELD_PASSWORD = "password";

    public static final java.lang.String FIELD_EMAIL = "email";

    public static final java.lang.String FIELD_GATEWAY_ACCOUNT_IDS = "gateway_account_ids";

    public static final java.lang.String FIELD_SERVICE_EXTERNAL_IDS = "service_external_ids";

    public static final java.lang.String FIELD_TELEPHONE_NUMBER = "telephone_number";

    public static final java.lang.String FIELD_OTP_KEY = "otp_key";

    public static final java.lang.String FIELD_ROLE_NAME = "role_name";

    public static final java.lang.String FIELD_FEATURES = "features";

    @io.swagger.v3.oas.annotations.media.Schema(example = "user@somegovernmentdept.gov.uk")
    private java.lang.String username;

    private java.lang.String password;

    @io.swagger.v3.oas.annotations.media.Schema(example = "user@somegovernmentdept.gov.uk")
    private java.lang.String email;

    @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @io.swagger.v3.oas.annotations.media.Schema(example = "1"))
    private java.util.List<java.lang.String> gatewayAccountIds;

    @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @io.swagger.v3.oas.annotations.media.Schema(example = "7d19aff33f8948deb97ed16b2912dcd3"))
    private java.util.List<java.lang.String> serviceExternalIds;

    @io.swagger.v3.oas.annotations.media.Schema(example = "447700900000")
    private java.lang.String telephoneNumber;

    @io.swagger.v3.oas.annotations.media.Schema(example = "43c3c4t")
    private java.lang.String otpKey;

    @io.swagger.v3.oas.annotations.media.Schema(example = "feature1, feature2")
    private java.lang.String features;

    public static uk.gov.pay.adminusers.model.CreateUserRequest from(java.lang.String username, java.lang.String password, java.lang.String email, java.util.List<java.lang.String> gatewayAccountIds, java.util.List<java.lang.String> serviceExternalIds, java.lang.String otpKey, java.lang.String telephoneNumber, java.lang.String features) {
        return new uk.gov.pay.adminusers.model.CreateUserRequest(username, password, email, gatewayAccountIds, serviceExternalIds, otpKey, telephoneNumber, features);
    }

    public static uk.gov.pay.adminusers.model.CreateUserRequest from(com.fasterxml.jackson.databind.JsonNode node) {
        final java.util.List<java.lang.String> gatewayAccountIds = uk.gov.pay.adminusers.model.CreateUserRequest.safelyGetList(node, uk.gov.pay.adminusers.model.CreateUserRequest.FIELD_GATEWAY_ACCOUNT_IDS);
        gatewayAccountIds.sort(uk.gov.pay.adminusers.utils.Comparators.numericallyThenLexicographically());
        final java.util.List<java.lang.String> serviceExternalIds = uk.gov.pay.adminusers.model.CreateUserRequest.safelyGetList(node, uk.gov.pay.adminusers.model.CreateUserRequest.FIELD_SERVICE_EXTERNAL_IDS);
        java.lang.String username = uk.gov.pay.adminusers.model.CreateUserRequest.getNodeAsTextOrFail(node, uk.gov.pay.adminusers.model.CreateUserRequest.FIELD_USERNAME);
        java.lang.String password = uk.gov.pay.adminusers.model.CreateUserRequest.getOrElseRandom(node.get(uk.gov.pay.adminusers.model.CreateUserRequest.FIELD_PASSWORD), uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid());
        java.lang.String email = uk.gov.pay.adminusers.model.CreateUserRequest.getNodeAsTextOrFail(node, uk.gov.pay.adminusers.model.CreateUserRequest.FIELD_EMAIL);
        java.lang.String telephoneNumber = uk.gov.pay.adminusers.model.CreateUserRequest.getNodeAsTextOrFail(node, uk.gov.pay.adminusers.model.CreateUserRequest.FIELD_TELEPHONE_NUMBER);
        java.lang.String otpKey = uk.gov.pay.adminusers.model.CreateUserRequest.getOrElseRandom(node.get(uk.gov.pay.adminusers.model.CreateUserRequest.FIELD_OTP_KEY), uk.gov.pay.adminusers.app.util.RandomIdGenerator.newId());
        java.lang.String features = uk.gov.pay.adminusers.model.CreateUserRequest.getOrElseRandom(node.get(uk.gov.pay.adminusers.model.CreateUserRequest.FIELD_FEATURES), null);
        return uk.gov.pay.adminusers.model.CreateUserRequest.from(username, password, email, gatewayAccountIds, serviceExternalIds, otpKey, telephoneNumber, features);
    }

    private static java.util.List<java.lang.String> safelyGetList(com.fasterxml.jackson.databind.JsonNode node, java.lang.String fieldName) {
        if ((node == null) || (node.get(fieldName) == null)) {
            return java.util.Collections.emptyList();
        }
        var results = new java.util.ArrayList<java.lang.String>();
        node.get(fieldName).iterator().forEachRemaining(nodeValue -> java.util.Optional.ofNullable(nodeValue).map(com.fasterxml.jackson.databind.JsonNode::asText).ifPresent(results::add));
        return results;
    }

    private static java.lang.String getNodeAsTextOrFail(com.fasterxml.jackson.databind.JsonNode node, java.lang.String fieldName) {
        return java.util.Optional.ofNullable(node.get(fieldName)).map(com.fasterxml.jackson.databind.JsonNode::asText).orElseThrow(() -> new java.lang.RuntimeException(java.lang.String.format("Error retrieving field %s for creating a user", fieldName)));
    }

    private static java.lang.String getOrElseRandom(com.fasterxml.jackson.databind.JsonNode elementNode, java.lang.String randomValue) {
        return (elementNode == null) || org.apache.commons.lang3.StringUtils.isBlank(elementNode.asText()) ? randomValue : elementNode.asText();
    }

    private CreateUserRequest(@com.fasterxml.jackson.annotation.JsonProperty("username")
    java.lang.String username, @com.fasterxml.jackson.annotation.JsonProperty("password")
    java.lang.String password, @com.fasterxml.jackson.annotation.JsonProperty("email")
    java.lang.String email, @com.fasterxml.jackson.annotation.JsonProperty("gateway_account_ids")
    java.util.List<java.lang.String> gatewayAccountIds, @com.fasterxml.jackson.annotation.JsonProperty("service_external_ids")
    java.util.List<java.lang.String> serviceExternalIds, @com.fasterxml.jackson.annotation.JsonProperty("otp_key")
    java.lang.String otpKey, @com.fasterxml.jackson.annotation.JsonProperty("telephone_number")
    java.lang.String telephoneNumber, @com.fasterxml.jackson.annotation.JsonProperty("features")
    java.lang.String features) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.gatewayAccountIds = gatewayAccountIds;
        this.serviceExternalIds = serviceExternalIds;
        this.otpKey = otpKey;
        this.telephoneNumber = telephoneNumber;
        this.features = features;
    }

    public java.lang.String getUsername() {
        return username;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public java.lang.String getPassword() {
        return password;
    }

    public java.lang.String getEmail() {
        return email;
    }

    public java.util.List<java.lang.String> getGatewayAccountIds() {
        return gatewayAccountIds;
    }

    public java.lang.String getOtpKey() {
        return otpKey;
    }

    public java.lang.String getTelephoneNumber() {
        return telephoneNumber;
    }

    public java.lang.String getFeatures() {
        return features;
    }

    public java.util.List<java.lang.String> getServiceExternalIds() {
        return serviceExternalIds;
    }
}
