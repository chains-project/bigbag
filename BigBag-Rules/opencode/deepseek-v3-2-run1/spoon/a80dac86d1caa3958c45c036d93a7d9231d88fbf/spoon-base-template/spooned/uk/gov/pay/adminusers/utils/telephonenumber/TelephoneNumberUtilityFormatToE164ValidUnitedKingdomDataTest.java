package uk.gov.pay.adminusers.utils.telephonenumber;
public class TelephoneNumberUtilityFormatToE164ValidUnitedKingdomDataTest {
    private static final java.lang.String TEST_RESULT = "+441134960000";

    public static java.lang.Object[] data() {
        return new java.lang.Object[]{ // local format
        "01134960000", "0113 496 0000", "0113-496-0000", "(0113) 496 0000", "(0113) 496-0000", "(0113) / 496-0000", "   01134960000   ", // international format
        "+441134960000", "+44113 496 0000", "+44113-496-0000", "(+44113) 496 0000", "(+44113) 496-0000", "(+44113) / 496-0000", "   +441134960000   ", "00441134960000", "0044113 496 0000", "0044113-496-0000", "(0044113) 496 0000", "(0044113) 496-0000", "(0044113) / 496-0000", "   00441134960000   " };
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("data")
    public void formatToE164_shouldEvaluateToE164FormattedTelephoneNumber(java.lang.String telephoneNumber) {
        java.lang.String result = uk.gov.pay.adminusers.utils.telephonenumber.TelephoneNumberUtility.formatToE164(telephoneNumber);
        org.hamcrest.MatcherAssert.assertThat(result, org.hamcrest.core.Is.is(uk.gov.pay.adminusers.utils.telephonenumber.TelephoneNumberUtilityFormatToE164ValidUnitedKingdomDataTest.TEST_RESULT));
    }
}
