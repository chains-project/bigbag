package uk.gov.pay.adminusers.persistence.entity;
public final class MerchantDetailsEntityBuilder {
    private java.lang.String name = "test-name";

    private java.lang.String telephoneNumber = "0123456789";

    private java.lang.String addressLine1 = "test-line-1";

    private java.lang.String addressLine2 = "test-line-2";

    private java.lang.String addressCity = "test-address-2";

    private java.lang.String addressPostcode = "test-postcode";

    private java.lang.String addressCountryCode = "GB";

    private java.lang.String email = "merchant@example.com";

    private java.lang.String url = "https://merchant.example.com";

    private MerchantDetailsEntityBuilder() {
    }

    public static uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntityBuilder aMerchantDetailsEntity() {
        return new uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntityBuilder();
    }

    public uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntityBuilder withName(java.lang.String name) {
        this.name = name;
        return this;
    }

    public uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntityBuilder withTelephoneNumber(java.lang.String telephoneNumber) {
        this.telephoneNumber = telephoneNumber;
        return this;
    }

    public uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntityBuilder withAddressLine1(java.lang.String addressLine1) {
        this.addressLine1 = addressLine1;
        return this;
    }

    public uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntityBuilder withAddressLine2(java.lang.String addressLine2) {
        this.addressLine2 = addressLine2;
        return this;
    }

    public uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntityBuilder withAddressCity(java.lang.String addressCity) {
        this.addressCity = addressCity;
        return this;
    }

    public uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntityBuilder withAddressPostcode(java.lang.String addressPostcode) {
        this.addressPostcode = addressPostcode;
        return this;
    }

    public uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntityBuilder withAddressCountryCode(java.lang.String addressCountryCode) {
        this.addressCountryCode = addressCountryCode;
        return this;
    }

    public uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntityBuilder withEmail(java.lang.String email) {
        this.email = email;
        return this;
    }

    public uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntityBuilder withUrl(java.lang.String url) {
        this.url = url;
        return this;
    }

    public uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity build() {
        uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity merchantDetailsEntity = new uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity();
        merchantDetailsEntity.setName(name);
        merchantDetailsEntity.setTelephoneNumber(telephoneNumber);
        merchantDetailsEntity.setAddressLine1(addressLine1);
        merchantDetailsEntity.setAddressLine2(addressLine2);
        merchantDetailsEntity.setAddressCity(addressCity);
        merchantDetailsEntity.setAddressPostcode(addressPostcode);
        merchantDetailsEntity.setAddressCountryCode(addressCountryCode);
        merchantDetailsEntity.setEmail(email);
        merchantDetailsEntity.setUrl(url);
        return merchantDetailsEntity;
    }
}
