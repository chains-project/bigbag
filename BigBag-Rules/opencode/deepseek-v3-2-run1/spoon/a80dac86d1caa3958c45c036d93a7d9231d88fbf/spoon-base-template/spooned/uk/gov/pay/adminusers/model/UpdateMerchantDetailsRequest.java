package uk.gov.pay.adminusers.model;
@com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UpdateMerchantDetailsRequest {
    private static final java.lang.String FIELD_NAME = "name";

    private static final java.lang.String FIELD_TELEPHONE_NUMBER = "telephone_number";

    private static final java.lang.String FIELD_ADDRESS_LINE1 = "address_line1";

    private static final java.lang.String FIELD_ADDRESS_LINE2 = "address_line2";

    private static final java.lang.String FIELD_ADDRESS_CITY = "address_city";

    private static final java.lang.String FIELD_ADDRESS_POSTCODE = "address_postcode";

    private static final java.lang.String FIELD_ADDRESS_COUNTRY = "address_country";

    private static final java.lang.String FIELD_EMAIL = "email";

    private static final java.lang.String FIELD_URL = "url";

    @io.swagger.v3.oas.annotations.media.Schema(example = "organisation name", required = true)
    private java.lang.String name;

    @io.swagger.v3.oas.annotations.media.Schema(example = "447700900000")
    private java.lang.String telephoneNumber;

    @io.swagger.v3.oas.annotations.media.Schema(example = "Address line 1", required = true)
    private java.lang.String addressLine1;

    @io.swagger.v3.oas.annotations.media.Schema(example = "Address line 2")
    private java.lang.String addressLine2;

    @io.swagger.v3.oas.annotations.media.Schema(example = "London", required = true)
    private java.lang.String addressCity;

    @io.swagger.v3.oas.annotations.media.Schema(example = "E1 8XX", required = true)
    private java.lang.String addressPostcode;

    @io.swagger.v3.oas.annotations.media.Schema(example = "GB", required = true)
    private java.lang.String addressCountry;

    @io.swagger.v3.oas.annotations.media.Schema(example = "email@example.com")
    private java.lang.String email;

    @io.swagger.v3.oas.annotations.media.Schema(example = "http://www.example.org")
    private java.lang.String url;

    public static uk.gov.pay.adminusers.model.UpdateMerchantDetailsRequest from(com.fasterxml.jackson.databind.JsonNode node) {
        java.lang.String name = node.get(uk.gov.pay.adminusers.model.UpdateMerchantDetailsRequest.FIELD_NAME).asText();
        java.lang.String telephoneNumber = java.util.Optional.ofNullable(node.get(uk.gov.pay.adminusers.model.UpdateMerchantDetailsRequest.FIELD_TELEPHONE_NUMBER)).map(com.fasterxml.jackson.databind.JsonNode::asText).orElse(null);
        java.lang.String addressLine1 = node.get(uk.gov.pay.adminusers.model.UpdateMerchantDetailsRequest.FIELD_ADDRESS_LINE1).asText();
        java.lang.String addressCity = node.get(uk.gov.pay.adminusers.model.UpdateMerchantDetailsRequest.FIELD_ADDRESS_CITY).asText();
        java.lang.String addressPostcode = node.get(uk.gov.pay.adminusers.model.UpdateMerchantDetailsRequest.FIELD_ADDRESS_POSTCODE).asText();
        java.lang.String addressCountry = node.get(uk.gov.pay.adminusers.model.UpdateMerchantDetailsRequest.FIELD_ADDRESS_COUNTRY).asText();
        java.lang.String addressLine2 = java.util.Optional.ofNullable(node.get(uk.gov.pay.adminusers.model.UpdateMerchantDetailsRequest.FIELD_ADDRESS_LINE2)).map(com.fasterxml.jackson.databind.JsonNode::asText).orElse(null);
        java.lang.String email = java.util.Optional.ofNullable(node.get(uk.gov.pay.adminusers.model.UpdateMerchantDetailsRequest.FIELD_EMAIL)).map(com.fasterxml.jackson.databind.JsonNode::asText).orElse(null);
        java.lang.String url = java.util.Optional.ofNullable(node.get(uk.gov.pay.adminusers.model.UpdateMerchantDetailsRequest.FIELD_URL)).map(com.fasterxml.jackson.databind.JsonNode::asText).orElse(null);
        return new uk.gov.pay.adminusers.model.UpdateMerchantDetailsRequest(name, telephoneNumber, addressLine1, addressLine2, addressCity, addressPostcode, addressCountry, email, url);
    }

    public UpdateMerchantDetailsRequest(@com.fasterxml.jackson.annotation.JsonProperty("name")
    java.lang.String name, @com.fasterxml.jackson.annotation.JsonProperty("telephone_number")
    java.lang.String telephoneNumber, @com.fasterxml.jackson.annotation.JsonProperty("address_line1")
    java.lang.String addressLine1, @com.fasterxml.jackson.annotation.JsonProperty("address_line2")
    java.lang.String addressLine2, @com.fasterxml.jackson.annotation.JsonProperty("address_city")
    java.lang.String addressCity, @com.fasterxml.jackson.annotation.JsonProperty("address_postcode")
    java.lang.String addressPostcode, @com.fasterxml.jackson.annotation.JsonProperty("address_country")
    java.lang.String addressCountry, @com.fasterxml.jackson.annotation.JsonProperty("email")
    java.lang.String email, @com.fasterxml.jackson.annotation.JsonProperty("url")
    java.lang.String url) {
        this.name = name;
        this.telephoneNumber = telephoneNumber;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.addressCity = addressCity;
        this.addressPostcode = addressPostcode;
        this.addressCountry = addressCountry;
        this.email = email;
        this.url = url;
    }

    public java.lang.String getName() {
        return name;
    }

    public java.lang.String getTelephoneNumber() {
        return telephoneNumber;
    }

    public java.lang.String getAddressLine1() {
        return addressLine1;
    }

    public java.lang.String getAddressLine2() {
        return addressLine2;
    }

    public java.lang.String getAddressCity() {
        return addressCity;
    }

    public java.lang.String getAddressPostcode() {
        return addressPostcode;
    }

    public java.lang.String getAddressCountry() {
        return addressCountry;
    }

    public java.lang.String getEmail() {
        return this.email;
    }

    public java.lang.String getUrl() {
        return this.url;
    }
}
