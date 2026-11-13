package uk.gov.pay.adminusers.model;
@com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
public class MerchantDetails {
    @io.swagger.v3.oas.annotations.media.Schema(example = "organisation name")
    private java.lang.String name;

    @io.swagger.v3.oas.annotations.media.Schema(example = "447700900000")
    private java.lang.String telephoneNumber;

    @io.swagger.v3.oas.annotations.media.Schema(example = "Address line 1")
    private java.lang.String addressLine1;

    @io.swagger.v3.oas.annotations.media.Schema(example = "Address line 2")
    private java.lang.String addressLine2;

    @io.swagger.v3.oas.annotations.media.Schema(example = "London")
    private java.lang.String addressCity;

    @io.swagger.v3.oas.annotations.media.Schema(example = "E1 8XX")
    private java.lang.String addressPostcode;

    @io.swagger.v3.oas.annotations.media.Schema(example = "GB")
    private java.lang.String addressCountry;

    @io.swagger.v3.oas.annotations.media.Schema(example = "email@example.com")
    private java.lang.String email;

    @io.swagger.v3.oas.annotations.media.Schema(example = "http://www.example.org")
    private java.lang.String url;

    public MerchantDetails() {
    }

    public MerchantDetails(@com.fasterxml.jackson.annotation.JsonProperty("name")
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
        return url;
    }

    @java.lang.Override
    public boolean equals(java.lang.Object o) {
        if (this == o) {
            return true;
        }
        if ((o == null) || (getClass() != o.getClass())) {
            return false;
        }
        uk.gov.pay.adminusers.model.MerchantDetails that = ((uk.gov.pay.adminusers.model.MerchantDetails) (o));
        return ((((((java.util.Objects.equals(name, that.name) && java.util.Objects.equals(telephoneNumber, that.telephoneNumber)) && java.util.Objects.equals(addressLine1, that.addressLine1)) && java.util.Objects.equals(addressLine2, that.addressLine2)) && java.util.Objects.equals(addressCity, that.addressCity)) && java.util.Objects.equals(addressPostcode, that.addressPostcode)) && java.util.Objects.equals(email, that.email)) && java.util.Objects.equals(addressCountry, that.addressCountry);
    }

    @java.lang.Override
    public int hashCode() {
        return java.util.Objects.hash(telephoneNumber, addressLine1, addressLine2, addressCity, addressPostcode, addressCountry, email);
    }
}
