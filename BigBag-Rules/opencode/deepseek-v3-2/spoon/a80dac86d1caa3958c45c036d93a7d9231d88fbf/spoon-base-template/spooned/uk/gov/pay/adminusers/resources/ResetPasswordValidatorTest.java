package uk.gov.pay.adminusers.resources;
public class ResetPasswordValidatorTest {
    private uk.gov.pay.adminusers.resources.ResetPasswordValidator resetPasswordValidator;

    private uk.gov.pay.adminusers.persistence.dao.ForgottenPasswordDao forgottenPasswordDao;

    @org.junit.jupiter.api.BeforeEach
    public void before() {
        forgottenPasswordDao = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.dao.ForgottenPasswordDao.class);
        resetPasswordValidator = new uk.gov.pay.adminusers.resources.ResetPasswordValidator(new uk.gov.pay.adminusers.validations.RequestValidations());
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnErrors_ifJsonNodeIsNull() {
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> errorsOptional = resetPasswordValidator.validateResetRequest(null);
        junit.framework.TestCase.assertTrue(errorsOptional.isPresent());
        org.hamcrest.MatcherAssert.assertThat(errorsOptional.get().getErrors().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(errorsOptional.get().getErrors(), org.hamcrest.core.IsIterableContaining.hasItem("invalid JSON"));
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnErrors_ifForgottenPasswordCodeIsBlank() {
        com.fasterxml.jackson.databind.JsonNode jsonNode = org.mockito.Mockito.mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode codeMock = org.mockito.Mockito.mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode passwordMock = org.mockito.Mockito.mock(com.fasterxml.jackson.databind.JsonNode.class);
        org.mockito.Mockito.when(jsonNode.get("forgotten_password_code")).thenReturn(codeMock);
        org.mockito.Mockito.when(jsonNode.get("new_password")).thenReturn(passwordMock);
        org.mockito.Mockito.when(codeMock.asText()).thenReturn(" ");
        org.mockito.Mockito.when(passwordMock.asText()).thenReturn("myNewPassword");
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> errorsOptional = resetPasswordValidator.validateResetRequest(jsonNode);
        junit.framework.TestCase.assertTrue(errorsOptional.isPresent());
        org.hamcrest.MatcherAssert.assertThat(errorsOptional.get().getErrors().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(errorsOptional.get().getErrors(), org.hamcrest.core.IsIterableContaining.hasItem("Field [forgotten_password_code] is required"));
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnErrors_ifForgottenPasswordCodeExceeds255Characters() {
        com.fasterxml.jackson.databind.JsonNode jsonNode = org.mockito.Mockito.mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode codeMock = org.mockito.Mockito.mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode passwordMock = org.mockito.Mockito.mock(com.fasterxml.jackson.databind.JsonNode.class);
        org.mockito.Mockito.when(jsonNode.get("forgotten_password_code")).thenReturn(codeMock);
        org.mockito.Mockito.when(jsonNode.get("new_password")).thenReturn(passwordMock);
        org.mockito.Mockito.when(codeMock.asText()).thenReturn(org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric(256));
        org.mockito.Mockito.when(passwordMock.asText()).thenReturn("myNewPassword");
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> errorsOptional = resetPasswordValidator.validateResetRequest(jsonNode);
        junit.framework.TestCase.assertTrue(errorsOptional.isPresent());
        org.hamcrest.MatcherAssert.assertThat(errorsOptional.get().getErrors().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(errorsOptional.get().getErrors(), org.hamcrest.core.IsIterableContaining.hasItem("Field [forgotten_password_code] must have a maximum length of 255 characters"));
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnErrors_ifNewPasswordIsBlank() {
        com.fasterxml.jackson.databind.JsonNode jsonNode = org.mockito.Mockito.mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode codeMock = org.mockito.Mockito.mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode passwordMock = org.mockito.Mockito.mock(com.fasterxml.jackson.databind.JsonNode.class);
        org.mockito.Mockito.when(jsonNode.get("forgotten_password_code")).thenReturn(codeMock);
        org.mockito.Mockito.when(jsonNode.get("new_password")).thenReturn(passwordMock);
        org.mockito.Mockito.when(codeMock.asText()).thenReturn("myCode");
        org.mockito.Mockito.when(passwordMock.asText()).thenReturn(" ");
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> errorsOptional = resetPasswordValidator.validateResetRequest(jsonNode);
        junit.framework.TestCase.assertTrue(errorsOptional.isPresent());
        org.hamcrest.MatcherAssert.assertThat(errorsOptional.get().getErrors().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(errorsOptional.get().getErrors(), org.hamcrest.core.IsIterableContaining.hasItem("Field [new_password] is required"));
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnEmptyOptional() {
        com.fasterxml.jackson.databind.JsonNode jsonNode = org.mockito.Mockito.mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode codeMock = org.mockito.Mockito.mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode passwordMock = org.mockito.Mockito.mock(com.fasterxml.jackson.databind.JsonNode.class);
        org.mockito.Mockito.when(jsonNode.get("forgotten_password_code")).thenReturn(codeMock);
        org.mockito.Mockito.when(codeMock.asText()).thenReturn("a-valid-code");
        org.mockito.Mockito.when(jsonNode.get("new_password")).thenReturn(passwordMock);
        org.mockito.Mockito.when(passwordMock.asText()).thenReturn("password");
        org.mockito.Mockito.when(forgottenPasswordDao.findNonExpiredByCode(jsonNode.asText())).thenReturn(java.util.Optional.empty());
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> errorsOptional = resetPasswordValidator.validateResetRequest(jsonNode);
        junit.framework.TestCase.assertFalse(errorsOptional.isPresent());
    }
}
