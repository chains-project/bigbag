package uk.gov.pay.adminusers.persistence.entity;
@javax.persistence.Embeddable
public class MerchantDetailsEntity {
    @javax.persistence.Column(name = "merchant_name")
    private java.lang.String name;

    @javax.persistence.Column(name = "merchant_telephone_number")
    private java.lang.String telephoneNumber;

    @javax.persistence.Column(name = "merchant_address_line1")
    private java.lang.String addressLine1;

    @javax.persistence.Column(name = "merchant_address_line2")
    private java.lang.String addressLine2;

    @javax.persistence.Column(name = "merchant_address_city")
    private java.lang.String addressCity;

    @javax.persistence.Column(name = "merchant_address_postcode")
    private java.lang.String addressPostcode;

    @javax.persistence.Column(name = "merchant_address_country")
    private java.lang.String addressCountryCode;

    @javax.persistence.Column(name = "merchant_email")
    private java.lang.String email;

    @javax.persistence.Column(name = "merchant_url")
    private java.lang.String url;

    // JPA requires default constructor
    public MerchantDetailsEntity() {
    }

    public MerchantDetailsEntity(java.lang.String name, java.lang.String telephoneNumber, java.lang.String addressLine1, java.lang.String addressLine2, java.lang.String addressCity, java.lang.String addressPostcode, java.lang.String addressCountry, java.lang.String email, java.lang.String url) {
        this.name = name;
        this.telephoneNumber = telephoneNumber;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.addressCity = addressCity;
        this.addressPostcode = addressPostcode;
        this.addressCountryCode = addressCountry;
        this.email = email;
        this.url = url;
    }

    public java.lang.String getName() {
        return name;
    }

    public void setName(java.lang.String name) {
        this.name = name;
    }

    public java.lang.String getTelephoneNumber() {
        return telephoneNumber;
    }

    public void setTelephoneNumber(java.lang.String telephoneNumber) {
        this.telephoneNumber = telephoneNumber;
    }

    public java.lang.String getAddressLine1() {
        return addressLine1;
    }

    public void setAddressLine1(java.lang.String addressLine1) {
        this.addressLine1 = addressLine1;
    }

    public java.lang.String getAddressLine2() {
        return addressLine2;
    }

    public void setAddressLine2(java.lang.String addressLine2) {
        this.addressLine2 = addressLine2;
    }

    public java.lang.String getAddressCity() {
        return addressCity;
    }

    public void setAddressCity(java.lang.String addressCity) {
        this.addressCity = addressCity;
    }

    public java.lang.String getAddressPostcode() {
        return addressPostcode;
    }

    public void setAddressPostcode(java.lang.String addressPostcode) {
        this.addressPostcode = addressPostcode;
    }

    public java.lang.String getAddressCountryCode() {
        return addressCountryCode;
    }

    public void setAddressCountryCode(java.lang.String addressCountryCode) {
        this.addressCountryCode = addressCountryCode;
    }

    public java.lang.String getEmail() {
        return email;
    }

    public void setEmail(java.lang.String email) {
        this.email = email;
    }

    public void setUrl(java.lang.String url) {
        this.url = url;
    }

    public java.lang.String getUrl() {
        return url;
    }

    public uk.gov.pay.adminusers.model.MerchantDetails toMerchantDetails() {
        return new uk.gov.pay.adminusers.model.MerchantDetails(this.name, this.telephoneNumber, this.addressLine1, this.addressLine2, this.addressCity, this.addressPostcode, this.addressCountryCode, this.email, this.url);
    }

    public static uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity from(uk.gov.pay.adminusers.model.UpdateMerchantDetailsRequest updateMerchantDetailsRequest) {
        return new uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity(updateMerchantDetailsRequest.getName(), updateMerchantDetailsRequest.getTelephoneNumber(), updateMerchantDetailsRequest.getAddressLine1(), updateMerchantDetailsRequest.getAddressLine2(), updateMerchantDetailsRequest.getAddressCity(), updateMerchantDetailsRequest.getAddressPostcode(), updateMerchantDetailsRequest.getAddressCountry(), updateMerchantDetailsRequest.getEmail(), updateMerchantDetailsRequest.getUrl());
    }

    @java.lang.Override
    public boolean equals(java.lang.Object o) {
        if (this == o) {
            return true;
        }
        if ((o == null) || (getClass() != o.getClass())) {
            return false;
        }
        uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity that = ((uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity) (o));
        return (((((((java.util.Objects.equals(name, that.name) && java.util.Objects.equals(telephoneNumber, that.telephoneNumber)) && java.util.Objects.equals(addressLine1, that.addressLine1)) && java.util.Objects.equals(addressLine2, that.addressLine2)) && java.util.Objects.equals(addressCity, that.addressCity)) && java.util.Objects.equals(addressPostcode, that.addressPostcode)) && java.util.Objects.equals(email, that.email)) && java.util.Objects.equals(addressCountryCode, that.addressCountryCode)) && java.util.Objects.equals(url, that.url);
    }

    @java.lang.Override
    public int hashCode() {
        return java.util.Objects.hash(name, telephoneNumber, addressLine1, addressLine2, addressCity, addressPostcode, addressCountryCode, email);
    }
}
