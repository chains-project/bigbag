package uk.gov.pay.adminusers.model;
class UpdateMerchantDetailsRequestTest {
    private static com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private final java.lang.String name = "name";

    private final java.lang.String telephoneNumber = "03069990000";

    private final java.lang.String addressLine1 = "address line1";

    private final java.lang.String addressLine2 = "address line2";

    private final java.lang.String addressCity = "city";

    private final java.lang.String addressCountry = "country";

    private final java.lang.String addressPostcode = "postcode";

    private final java.lang.String email = "dd-merchant@example.com";

    @org.junit.jupiter.api.Test
    void shouldConstructMerchantDetails_fromMinimalValidJson() {
        java.util.Map<java.lang.String, java.lang.Object> payload = java.util.Map.of("name", name, "address_line1", addressLine1, "address_city", addressCity, "address_country", addressCountry, "address_postcode", addressPostcode);
        com.fasterxml.jackson.databind.JsonNode jsonNode = uk.gov.pay.adminusers.model.UpdateMerchantDetailsRequestTest.objectMapper.valueToTree(payload);
        uk.gov.pay.adminusers.model.UpdateMerchantDetailsRequest updateMerchantDetailsRequest = uk.gov.pay.adminusers.model.UpdateMerchantDetailsRequest.from(jsonNode);
        org.hamcrest.MatcherAssert.assertThat(updateMerchantDetailsRequest.getName(), org.hamcrest.core.Is.is(name));
        org.hamcrest.MatcherAssert.assertThat(updateMerchantDetailsRequest.getAddressLine1(), org.hamcrest.core.Is.is(addressLine1));
        org.hamcrest.MatcherAssert.assertThat(updateMerchantDetailsRequest.getAddressLine2(), org.hamcrest.core.Is.is(org.hamcrest.core.IsNull.nullValue()));
        org.hamcrest.MatcherAssert.assertThat(updateMerchantDetailsRequest.getAddressCity(), org.hamcrest.core.Is.is(addressCity));
        org.hamcrest.MatcherAssert.assertThat(updateMerchantDetailsRequest.getAddressCountry(), org.hamcrest.core.Is.is(addressCountry));
        org.hamcrest.MatcherAssert.assertThat(updateMerchantDetailsRequest.getAddressPostcode(), org.hamcrest.core.Is.is(addressPostcode));
    }

    @org.junit.jupiter.api.Test
    void shouldConstructMerchantDetails_fromCompleteValidJson() {
        java.util.Map<java.lang.String, java.lang.Object> payload = java.util.Map.of("name", name, "telephone_number", telephoneNumber, "address_line1", addressLine1, "address_line2", addressLine2, "address_city", addressCity, "address_country", addressCountry, "address_postcode", addressPostcode, "email", email);
        com.fasterxml.jackson.databind.JsonNode jsonNode = uk.gov.pay.adminusers.model.UpdateMerchantDetailsRequestTest.objectMapper.valueToTree(payload);
        uk.gov.pay.adminusers.model.UpdateMerchantDetailsRequest updateMerchantDetailsRequest = uk.gov.pay.adminusers.model.UpdateMerchantDetailsRequest.from(jsonNode);
        org.hamcrest.MatcherAssert.assertThat(updateMerchantDetailsRequest.getName(), org.hamcrest.core.Is.is(name));
        org.hamcrest.MatcherAssert.assertThat(updateMerchantDetailsRequest.getTelephoneNumber(), org.hamcrest.core.Is.is(telephoneNumber));
        org.hamcrest.MatcherAssert.assertThat(updateMerchantDetailsRequest.getAddressLine1(), org.hamcrest.core.Is.is(addressLine1));
        org.hamcrest.MatcherAssert.assertThat(updateMerchantDetailsRequest.getAddressLine2(), org.hamcrest.core.Is.is(addressLine2));
        org.hamcrest.MatcherAssert.assertThat(updateMerchantDetailsRequest.getAddressCity(), org.hamcrest.core.Is.is(addressCity));
        org.hamcrest.MatcherAssert.assertThat(updateMerchantDetailsRequest.getAddressCountry(), org.hamcrest.core.Is.is(addressCountry));
        org.hamcrest.MatcherAssert.assertThat(updateMerchantDetailsRequest.getAddressPostcode(), org.hamcrest.core.Is.is(addressPostcode));
        org.hamcrest.MatcherAssert.assertThat(updateMerchantDetailsRequest.getEmail(), org.hamcrest.core.Is.is(email));
    }
}
