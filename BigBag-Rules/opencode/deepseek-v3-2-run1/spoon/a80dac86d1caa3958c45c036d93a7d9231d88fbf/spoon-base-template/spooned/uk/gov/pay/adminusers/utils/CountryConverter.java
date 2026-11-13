package uk.gov.pay.adminusers.utils;
public class CountryConverter {
    private static final java.lang.String COUNTRIES_FILE_PATH = "countries.json";

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private final java.util.Map<java.lang.String, java.lang.String> countries;

    @com.google.inject.Inject
    public CountryConverter(com.fasterxml.jackson.databind.ObjectMapper objectMapper) throws java.io.IOException {
        this.objectMapper = objectMapper;
        java.lang.String textCountries = com.google.common.io.Resources.toString(com.google.common.io.Resources.getResource(uk.gov.pay.adminusers.utils.CountryConverter.COUNTRIES_FILE_PATH), java.nio.charset.StandardCharsets.UTF_8);
        this.countries = createMap(textCountries);
    }

    private java.util.Map<java.lang.String, java.lang.String> createMap(java.lang.String countries) throws java.io.IOException {
        java.util.List<java.util.List<java.lang.String>> allCountries = objectMapper.readValue(countries, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        return allCountries.stream().filter(country -> country.get(1).startsWith("country:")).collect(java.util.stream.Collectors.toUnmodifiableMap(country -> uk.gov.pay.adminusers.utils.CountryConverter.getIsoCode(country.get(1)), country -> country.get(0)));
    }

    private static java.lang.String getIsoCode(java.lang.String typeAndIsoCode) {
        return typeAndIsoCode.substring(typeAndIsoCode.indexOf(':') + 1);
    }

    public java.util.Optional<java.lang.String> getCountryNameFrom(java.lang.String isoName) {
        return java.util.Optional.ofNullable(countries.get(isoName));
    }
}
