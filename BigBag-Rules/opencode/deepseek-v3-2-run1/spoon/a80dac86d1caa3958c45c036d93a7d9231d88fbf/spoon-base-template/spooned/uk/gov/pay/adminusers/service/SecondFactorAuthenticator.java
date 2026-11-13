package uk.gov.pay.adminusers.service;
public class SecondFactorAuthenticator {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(uk.gov.pay.adminusers.service.SecondFactorAuthenticator.class);

    private static final java.util.regex.Pattern BASE32_ALPHABET = java.util.regex.Pattern.compile("[A-Z2-7]+");

    private final com.warrenstrange.googleauth.GoogleAuthenticator authenticator;

    private final java.time.Clock clock;

    @com.google.inject.Inject
    public SecondFactorAuthenticator(com.warrenstrange.googleauth.GoogleAuthenticatorConfig authenticatorConfig, java.time.Clock clock) {
        this.clock = clock;
        this.authenticator = new com.warrenstrange.googleauth.GoogleAuthenticator(authenticatorConfig);
    }

    public int newPassCode(java.lang.String secret) {
        checkNull(secret);
        java.lang.String base32EncodedSecret = (uk.gov.pay.adminusers.service.SecondFactorAuthenticator.BASE32_ALPHABET.matcher(secret).matches()) ? secret : base32EncodedUtf8BytesOfSecret(secret);
        return authenticator.getTotpPassword(base32EncodedSecret, clock.millis());
    }

    public boolean authorize(java.lang.String secret, int passcode) {
        checkNull(secret);
        java.lang.String base32EncodedSecret = (uk.gov.pay.adminusers.service.SecondFactorAuthenticator.BASE32_ALPHABET.matcher(secret).matches()) ? secret : base32EncodedUtf8BytesOfSecret(secret);
        return authenticator.authorize(base32EncodedSecret, passcode, clock.millis());
    }

    public java.lang.String generateNewBase32EncodedSecret() {
        return authenticator.createCredentials().getKey();
    }

    private java.lang.String base32EncodedUtf8BytesOfSecret(java.lang.String secret) {
        // This seems to be to match the recommendations of notp, a
        // Node.js package we used to use to do OTP in self-service
        // https://github.com/guyht/notp/blob/master/Readme.md#google-authenticator
        return com.google.common.io.BaseEncoding.base32().encode(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void checkNull(java.lang.String secret) {
        if (org.apache.commons.lang3.StringUtils.isBlank(secret)) {
            java.lang.String error = "supplied a null/empty otpKey for second factor";
            uk.gov.pay.adminusers.service.SecondFactorAuthenticator.LOGGER.error(error);
            throw new java.lang.RuntimeException(error);
        }
    }
}
