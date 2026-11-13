package uk.gov.pay.adminusers.utils;
public class CountryConverterTest {
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private uk.gov.pay.adminusers.utils.CountryConverter countryConverter;

    @org.junit.jupiter.api.BeforeEach
    public void setUp() throws java.lang.Exception {
        this.countryConverter = new uk.gov.pay.adminusers.utils.CountryConverter(objectMapper);
    }

    @org.junit.jupiter.api.Test
    public void shouldGetCountryNameForAValidIsoCode() {
        org.hamcrest.MatcherAssert.assertThat(countryConverter.getCountryNameFrom("AA").get(), org.hamcrest.CoreMatchers.is("aaa"));
        org.hamcrest.MatcherAssert.assertThat(countryConverter.getCountryNameFrom("BB").get(), org.hamcrest.CoreMatchers.is("bbb"));
    }

    @org.junit.jupiter.api.Test
    public void shouldNotGetCountryNameForAValidIsoCode_ifIsoCodeDoesNotRepresentACountry() {
        org.hamcrest.MatcherAssert.assertThat(countryConverter.getCountryNameFrom("CC").isPresent(), org.hamcrest.CoreMatchers.is(false));
    }

    @org.junit.jupiter.api.Test
    public void shouldNotGetCountryNameFromIsoCodeNotPresentInCountriesList() {
        org.hamcrest.MatcherAssert.assertThat(countryConverter.getCountryNameFrom("alex").isPresent(), org.hamcrest.CoreMatchers.is(false));
    }
}
