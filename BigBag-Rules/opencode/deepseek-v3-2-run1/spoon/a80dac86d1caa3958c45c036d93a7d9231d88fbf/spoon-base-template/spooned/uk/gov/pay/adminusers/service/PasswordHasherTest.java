package uk.gov.pay.adminusers.service;
public class PasswordHasherTest {
    @org.junit.jupiter.api.Test
    public void shouldHashAPlainTextPassword() {
        uk.gov.pay.adminusers.service.PasswordHasher passwordHasher = new uk.gov.pay.adminusers.service.PasswordHasher();
        java.lang.String hashedPassword = passwordHasher.hash("plain text password");
        org.hamcrest.MatcherAssert.assertThat(hashedPassword, org.hamcrest.core.Is.is(org.hamcrest.CoreMatchers.not("plain text password")));
    }

    @org.junit.jupiter.api.Test
    public void shouldMatchToTrue_ifSamePassword() {
        uk.gov.pay.adminusers.service.PasswordHasher passwordHasher = new uk.gov.pay.adminusers.service.PasswordHasher();
        java.lang.String hashedPassword = passwordHasher.hash("plain text password");
        junit.framework.TestCase.assertTrue(passwordHasher.isEqual("plain text password", hashedPassword));
    }

    @org.junit.jupiter.api.Test
    public void shouldMatchToFalse_ifDifferentPassword() {
        uk.gov.pay.adminusers.service.PasswordHasher passwordHasher = new uk.gov.pay.adminusers.service.PasswordHasher();
        java.lang.String hashedPassword = passwordHasher.hash("existing password");
        junit.framework.TestCase.assertFalse(passwordHasher.isEqual("different password", hashedPassword));
    }
}
