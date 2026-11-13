package uk.gov.pay.adminusers.utils.telephonenumber;
public class TelephoneNumberUtilityIsValidPhoneNumberTest {
    public static java.util.Collection<java.lang.Object[]> data() {
        return java.util.Arrays.asList(new java.lang.Object[][]{ // valid phone numbers: local format
        new java.lang.Object[]{ "01134960000", true }, new java.lang.Object[]{ "0113 496 0000", true }, new java.lang.Object[]{ "0113-496-0000", true }, new java.lang.Object[]{ "(0113) 496 0000", true }, new java.lang.Object[]{ "(0113) 496-0000", true }, new java.lang.Object[]{ "(0113) / 496-0000", true }, new java.lang.Object[]{ "   01134960000   ", true }, new java.lang.Object[]{ "0113--496--0000", true }, new java.lang.Object[]{ "#01134960000", true }, new java.lang.Object[]{ "<01134960000", true }, new java.lang.Object[]{ ">01134960000", true }, new java.lang.Object[]{ "[0113]4960000", true }, // valid phone numbers: international format
        new java.lang.Object[]{ "+441134960000", true }, new java.lang.Object[]{ "+44113 496 0000", true }, new java.lang.Object[]{ "+44113-496-0000", true }, new java.lang.Object[]{ "(+44113) 496 0000", true }, new java.lang.Object[]{ "(+44113) 496-0000", true }, new java.lang.Object[]{ "(+44113) / 496-0000", true }, new java.lang.Object[]{ "   +441134960000   ", true }, new java.lang.Object[]{ "+44--113--496--0000", true }, new java.lang.Object[]{ "#+441134960000", true }, new java.lang.Object[]{ "<+441134960000", true }, new java.lang.Object[]{ ">+441134960000", true }, new java.lang.Object[]{ "+[44]1134960000", true }, new java.lang.Object[]{ "00441134960000", true }, new java.lang.Object[]{ "0044113 496 0000", true }, new java.lang.Object[]{ "0044113-496-0000", true }, new java.lang.Object[]{ "(0044113) 496 0000", true }, new java.lang.Object[]{ "(0044113) 496-0000", true }, new java.lang.Object[]{ "(0044113) / 496-0000", true }, new java.lang.Object[]{ "   00441134960000   ", true }, new java.lang.Object[]{ "0044--113--496--0000", true }, new java.lang.Object[]{ "#00441134960000", true }, new java.lang.Object[]{ "<00441134960000", true }, new java.lang.Object[]{ ">00441134960000", true }, new java.lang.Object[]{ "[0044]1134960000", true }, // invalid phone numbers
        new java.lang.Object[]{ null, false }, new java.lang.Object[]{ "", false }, new java.lang.Object[]{ "  ", false }, new java.lang.Object[]{ "07700900000", false }// example phone number (valid format, but invalid as a real phone number)
        // example phone number (valid format, but invalid as a real phone number)
        // example phone number (valid format, but invalid as a real phone number)
        , new java.lang.Object[]{ "0770 090 0000", false }, new java.lang.Object[]{ "0770-090-0000", false }, new java.lang.Object[]{ "(0770) 090 0000", false }, new java.lang.Object[]{ "(0770) 090-0000", false }, new java.lang.Object[]{ "(0770) / 090-0000", false }, new java.lang.Object[]{ "   07700900000   ", false }, new java.lang.Object[]{ "0113496443a", false }, new java.lang.Object[]{ "+44113496443a", false }, new java.lang.Object[]{ "0044113496443a", false }, new java.lang.Object[]{ "0770090000a", false }, new java.lang.Object[]{ "O2O793O0000", false }// letter "O" instead of "0"
        // letter "O" instead of "0"
        // letter "O" instead of "0"
        , new java.lang.Object[]{ "abc", false } });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("data")
    public void isValidPhoneNumber_shouldEvaluateWhetherOrNotItIsValidPhoneNumber(java.lang.String telephoneNumber, boolean testResult) {
        boolean result = uk.gov.pay.adminusers.utils.telephonenumber.TelephoneNumberUtility.isValidPhoneNumber(telephoneNumber);
        org.hamcrest.MatcherAssert.assertThat((("Expected " + telephoneNumber) + " to be ") + (testResult ? "valid" : "invalid"), result, org.hamcrest.core.Is.is(testResult));
    }
}
