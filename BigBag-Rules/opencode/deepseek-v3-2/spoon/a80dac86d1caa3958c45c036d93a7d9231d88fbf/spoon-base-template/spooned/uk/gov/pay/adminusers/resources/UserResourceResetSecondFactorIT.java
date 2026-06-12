package uk.gov.pay.adminusers.resources;
public class UserResourceResetSecondFactorIT extends uk.gov.pay.adminusers.resources.IntegrationTest {
    private static final java.lang.String OTP_KEY = "34f34";

    private java.lang.String externalId;

    @org.junit.jupiter.api.BeforeEach
    public void createValidUser() {
        uk.gov.pay.adminusers.model.User user = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(databaseHelper).withSecondFactorMethod(uk.gov.pay.adminusers.model.SecondFactorMethod.APP).withOtpKey(uk.gov.pay.adminusers.resources.UserResourceResetSecondFactorIT.OTP_KEY).insertUser();
        this.externalId = user.getExternalId();
    }

    @org.junit.jupiter.api.Test
    public void shouldResetSecondFactorMethod() {
        givenSetup().when().post(("v1/api/users/" + externalId) + "/reset-second-factor").then().statusCode(200).body("second_factor", org.hamcrest.core.Is.is("SMS"));
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnNotFound_whenUserNotFound() {
        givenSetup().when().post("v1/api/users/not-found/reset-second-factor").then().statusCode(404);
    }
}
