package uk.gov.pay.adminusers.service;
@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
public class SecondFactorAuthenticatorTest {
    private static final java.lang.String SECRET = "mysecret";

    private static final java.lang.String BASE32_ENCODED_SECRET = "KPWXGUTNWOE7PMVK";

    private static final java.time.Duration TIME_STEP = java.time.Duration.of(30, java.time.temporal.ChronoUnit.SECONDS);

    private static final int PAST_OR_FUTURE_WINDOWS_TO_CHECK = 4;

    private static final int PAST_PRESENT_AND_FUTURE_WINDOWS_TO_CHECK = (uk.gov.pay.adminusers.service.SecondFactorAuthenticatorTest.PAST_OR_FUTURE_WINDOWS_TO_CHECK * 2) + 1;

    private static final com.warrenstrange.googleauth.GoogleAuthenticatorConfig AUTH_CONFIG = new com.warrenstrange.googleauth.GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder().setWindowSize(uk.gov.pay.adminusers.service.SecondFactorAuthenticatorTest.PAST_PRESENT_AND_FUTURE_WINDOWS_TO_CHECK).setTimeStepSizeInMillis(uk.gov.pay.adminusers.service.SecondFactorAuthenticatorTest.TIME_STEP.toMillis()).build();

    @org.mockito.Mock
    private java.time.Clock clock;

    private java.time.Instant initialTime;

    private uk.gov.pay.adminusers.service.SecondFactorAuthenticator secondFactorAuthenticator;

    @org.junit.jupiter.api.BeforeEach
    public void before() {
        initialTime = java.time.Instant.now();
        org.mockito.Mockito.lenient().when(clock.millis()).thenReturn(initialTime.toEpochMilli());
        secondFactorAuthenticator = new uk.gov.pay.adminusers.service.SecondFactorAuthenticator(uk.gov.pay.adminusers.service.SecondFactorAuthenticatorTest.AUTH_CONFIG, clock);
    }

    @org.junit.jupiter.api.Test
    public void shouldGenerateAndValidate2FAPasscode() {
        int passCode = secondFactorAuthenticator.newPassCode(uk.gov.pay.adminusers.service.SecondFactorAuthenticatorTest.SECRET);
        org.junit.jupiter.api.Assertions.assertTrue(secondFactorAuthenticator.authorize(uk.gov.pay.adminusers.service.SecondFactorAuthenticatorTest.SECRET, passCode));
        org.junit.jupiter.api.Assertions.assertFalse(secondFactorAuthenticator.authorize("incorrectSecret", passCode));
    }

    @org.junit.jupiter.api.Test
    public void shouldGenerateAndValidate2FAPasscodeFromBase32EncodedSecret() {
        int passCode = secondFactorAuthenticator.newPassCode(uk.gov.pay.adminusers.service.SecondFactorAuthenticatorTest.BASE32_ENCODED_SECRET);
        org.junit.jupiter.api.Assertions.assertTrue(secondFactorAuthenticator.authorize(uk.gov.pay.adminusers.service.SecondFactorAuthenticatorTest.BASE32_ENCODED_SECRET, passCode));
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_ifAskedToValidateImmediateLastSteps2FAPasscode() {
        int passCode = secondFactorAuthenticator.newPassCode(uk.gov.pay.adminusers.service.SecondFactorAuthenticatorTest.SECRET);
        org.mockito.Mockito.when(clock.millis()).thenReturn(initialTime.plus(uk.gov.pay.adminusers.service.SecondFactorAuthenticatorTest.TIME_STEP).toEpochMilli());
        org.junit.jupiter.api.Assertions.assertTrue(secondFactorAuthenticator.authorize(uk.gov.pay.adminusers.service.SecondFactorAuthenticatorTest.SECRET, passCode));
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_ifAskedToValidateImmediateLastSteps2FAPasscode_fromBase32EncodedSecret() {
        int passCode = secondFactorAuthenticator.newPassCode(uk.gov.pay.adminusers.service.SecondFactorAuthenticatorTest.BASE32_ENCODED_SECRET);
        org.mockito.Mockito.when(clock.millis()).thenReturn(initialTime.plus(uk.gov.pay.adminusers.service.SecondFactorAuthenticatorTest.TIME_STEP).toEpochMilli());
        org.junit.jupiter.api.Assertions.assertTrue(secondFactorAuthenticator.authorize(uk.gov.pay.adminusers.service.SecondFactorAuthenticatorTest.BASE32_ENCODED_SECRET, passCode));
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_ifAskedToValidateAValidPastSteps2FAPasscode() {
        int passCode = secondFactorAuthenticator.newPassCode(uk.gov.pay.adminusers.service.SecondFactorAuthenticatorTest.SECRET);
        org.mockito.Mockito.when(clock.millis()).thenReturn(initialTime.plus(uk.gov.pay.adminusers.service.SecondFactorAuthenticatorTest.TIME_STEP.multipliedBy(uk.gov.pay.adminusers.service.SecondFactorAuthenticatorTest.PAST_OR_FUTURE_WINDOWS_TO_CHECK)).toEpochMilli());
        org.junit.jupiter.api.Assertions.assertTrue(secondFactorAuthenticator.authorize(uk.gov.pay.adminusers.service.SecondFactorAuthenticatorTest.SECRET, passCode));
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_ifAskedToValidateAValidPastSteps2FAPasscode_fromBase32EncodedSecret() {
        int passCode = secondFactorAuthenticator.newPassCode(uk.gov.pay.adminusers.service.SecondFactorAuthenticatorTest.BASE32_ENCODED_SECRET);
        org.mockito.Mockito.when(clock.millis()).thenReturn(initialTime.plus(uk.gov.pay.adminusers.service.SecondFactorAuthenticatorTest.TIME_STEP.multipliedBy(uk.gov.pay.adminusers.service.SecondFactorAuthenticatorTest.PAST_OR_FUTURE_WINDOWS_TO_CHECK)).toEpochMilli());
        org.junit.jupiter.api.Assertions.assertTrue(secondFactorAuthenticator.authorize(uk.gov.pay.adminusers.service.SecondFactorAuthenticatorTest.BASE32_ENCODED_SECRET, passCode));
    }

    @org.junit.jupiter.api.Test
    public void shouldError_ifAskedToValidate2FAPasscodeOlderThanLastValidStep() {
        int passCode = secondFactorAuthenticator.newPassCode(uk.gov.pay.adminusers.service.SecondFactorAuthenticatorTest.SECRET);
        org.mockito.Mockito.when(clock.millis()).thenReturn(initialTime.plus(uk.gov.pay.adminusers.service.SecondFactorAuthenticatorTest.TIME_STEP.multipliedBy(uk.gov.pay.adminusers.service.SecondFactorAuthenticatorTest.PAST_OR_FUTURE_WINDOWS_TO_CHECK + 1)).toEpochMilli());
        org.junit.jupiter.api.Assertions.assertFalse(secondFactorAuthenticator.authorize(uk.gov.pay.adminusers.service.SecondFactorAuthenticatorTest.SECRET, passCode));
    }

    @org.junit.jupiter.api.Test
    public void shouldError_ifAskedToValidate2FAPasscodeOlderThanLastValidStep_fromBase32EncodedSecret() {
        int passCode = secondFactorAuthenticator.newPassCode(uk.gov.pay.adminusers.service.SecondFactorAuthenticatorTest.BASE32_ENCODED_SECRET);
        org.mockito.Mockito.when(clock.millis()).thenReturn(initialTime.plus(uk.gov.pay.adminusers.service.SecondFactorAuthenticatorTest.TIME_STEP.multipliedBy(uk.gov.pay.adminusers.service.SecondFactorAuthenticatorTest.PAST_OR_FUTURE_WINDOWS_TO_CHECK + 1)).toEpochMilli());
        org.junit.jupiter.api.Assertions.assertFalse(secondFactorAuthenticator.authorize(uk.gov.pay.adminusers.service.SecondFactorAuthenticatorTest.BASE32_ENCODED_SECRET, passCode));
    }

    @org.junit.jupiter.api.Test
    public void shouldError_IfPasscodeIsNull_WhenCreate() {
        java.lang.RuntimeException exception = org.junit.jupiter.api.Assertions.assertThrows(java.lang.RuntimeException.class, () -> secondFactorAuthenticator.newPassCode(null));
        org.hamcrest.MatcherAssert.assertThat(exception.getMessage(), org.hamcrest.core.Is.is("supplied a null/empty otpKey for second factor"));
    }

    @org.junit.jupiter.api.Test
    public void shouldGenerateNewBase32EncodedSecret() {
        java.lang.String thirtyTwoCharacterBase32Regex = "[A-Z2-7]{32}";
        org.hamcrest.MatcherAssert.assertThat(secondFactorAuthenticator.generateNewBase32EncodedSecret(), org.hamcrest.text.MatchesPattern.matchesPattern(thirtyTwoCharacterBase32Regex));
    }
}
