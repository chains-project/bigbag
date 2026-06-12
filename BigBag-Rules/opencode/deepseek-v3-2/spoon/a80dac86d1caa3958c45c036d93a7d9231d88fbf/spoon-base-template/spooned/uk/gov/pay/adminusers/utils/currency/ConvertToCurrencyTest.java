package uk.gov.pay.adminusers.utils.currency;
class ConvertToCurrencyTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("penceProvider")
    void shouldCorrectlyCalculateConvertPenceToPounds(java.lang.Long pence, java.lang.String expected) {
        org.hamcrest.MatcherAssert.assertThat(uk.gov.pay.adminusers.utils.currency.ConvertToCurrency.convertPenceToPounds.apply(pence).toString(), org.hamcrest.CoreMatchers.is(expected));
    }

    private static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> penceProvider() {
        return java.util.stream.Stream.of(org.junit.jupiter.params.provider.Arguments.of(123L, "1.23"), org.junit.jupiter.params.provider.Arguments.of(12000L, "120.00"), org.junit.jupiter.params.provider.Arguments.of(1L, "0.01"), org.junit.jupiter.params.provider.Arguments.of(0L, "0.00"), org.junit.jupiter.params.provider.Arguments.of(-1500L, "-15.00"));
    }
}
