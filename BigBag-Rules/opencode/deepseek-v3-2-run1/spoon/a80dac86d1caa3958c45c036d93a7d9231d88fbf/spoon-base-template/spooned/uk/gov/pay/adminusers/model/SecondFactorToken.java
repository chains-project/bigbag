package uk.gov.pay.adminusers.model;
@com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy.class)
public class SecondFactorToken {
    private static final java.lang.String SIX_DIGITS_WITH_LEADING_ZEROS = "%06d";

    private final java.lang.String username;

    private final java.lang.String passcode;

    private SecondFactorToken(@com.fasterxml.jackson.annotation.JsonProperty("username")
    java.lang.String username, @com.fasterxml.jackson.annotation.JsonProperty("passcode")
    java.lang.String passcode) {
        this.username = username;
        this.passcode = passcode;
    }

    public static uk.gov.pay.adminusers.model.SecondFactorToken from(java.lang.String username, int passcode) {
        return new uk.gov.pay.adminusers.model.SecondFactorToken(username, java.lang.String.format(java.util.Locale.ENGLISH, uk.gov.pay.adminusers.model.SecondFactorToken.SIX_DIGITS_WITH_LEADING_ZEROS, passcode));
    }

    public java.lang.String getUsername() {
        return username;
    }

    @com.fasterxml.jackson.annotation.JsonGetter
    public java.lang.String getPasscode() {
        return passcode;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public int getPasscodeAsInt() {
        return java.lang.Integer.parseInt(passcode);
    }
}
