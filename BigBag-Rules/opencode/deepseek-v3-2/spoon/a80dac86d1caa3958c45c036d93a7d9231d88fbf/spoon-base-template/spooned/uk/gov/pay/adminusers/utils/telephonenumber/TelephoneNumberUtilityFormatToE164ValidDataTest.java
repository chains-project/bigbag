package uk.gov.pay.adminusers.utils.telephonenumber;
public class TelephoneNumberUtilityFormatToE164ValidDataTest {
    @org.junit.jupiter.api.Test
    public void formatToE164_shouldEvaluateToE164FormattedAmericanTelephoneNumber() {
        // Given
        java.lang.String telephoneNumber = "+13115552368";
        java.lang.String testResult = "+13115552368";
        // When
        java.lang.String result = uk.gov.pay.adminusers.utils.telephonenumber.TelephoneNumberUtility.formatToE164(telephoneNumber);
        // Then
        org.hamcrest.MatcherAssert.assertThat(result, org.hamcrest.core.Is.is(testResult));
    }
}
