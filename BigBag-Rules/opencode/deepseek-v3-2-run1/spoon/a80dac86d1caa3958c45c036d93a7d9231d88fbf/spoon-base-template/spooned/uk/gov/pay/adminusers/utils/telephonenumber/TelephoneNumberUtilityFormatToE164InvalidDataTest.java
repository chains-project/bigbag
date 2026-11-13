package uk.gov.pay.adminusers.utils.telephonenumber;
public class TelephoneNumberUtilityFormatToE164InvalidDataTest {
    public static java.lang.Object[] data() {
        return new java.lang.Object[]{ null, "", " ", "abc", "(╯°□°）╯︵ ┻━┻" };
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("data")
    public void formatToE164_shouldThrowNumberParseExceptionOnInvalidTelephoneNumber(java.lang.String telephoneNumber) {
        org.junit.jupiter.api.Assertions.assertThrows(java.lang.RuntimeException.class, () -> uk.gov.pay.adminusers.utils.telephonenumber.TelephoneNumberUtility.formatToE164(telephoneNumber));
    }
}
