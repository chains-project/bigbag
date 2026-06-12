package uk.gov.pay.adminusers.resources;
public class ForgottenPasswordValidatorTest {
    private uk.gov.pay.adminusers.resources.ForgottenPasswordValidator validator;

    @org.junit.jupiter.api.BeforeEach
    public void before() {
        validator = new uk.gov.pay.adminusers.resources.ForgottenPasswordValidator();
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnErrors_ifJsonNodeIsNull() {
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> errorsOptional = validator.validateCreateRequest(null);
        junit.framework.TestCase.assertTrue(errorsOptional.isPresent());
        org.hamcrest.MatcherAssert.assertThat(errorsOptional.get().getErrors(), org.hamcrest.core.IsIterableContaining.hasItem("Field [username] is required"));
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnErrors_ifNoUserNameElement() {
        com.fasterxml.jackson.databind.JsonNode jsonNode = org.mockito.Mockito.mock(com.fasterxml.jackson.databind.JsonNode.class);
        org.mockito.Mockito.when(jsonNode.get("username")).thenReturn(null);
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> errorsOptional = validator.validateCreateRequest(jsonNode);
        junit.framework.TestCase.assertTrue(errorsOptional.isPresent());
        org.hamcrest.MatcherAssert.assertThat(errorsOptional.get().getErrors(), org.hamcrest.core.IsIterableContaining.hasItem("Field [username] is required"));
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnErrors_ifUsernameIsBlank() {
        com.fasterxml.jackson.databind.JsonNode jsonNode = org.mockito.Mockito.mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode userNameMock = org.mockito.Mockito.mock(com.fasterxml.jackson.databind.JsonNode.class);
        org.mockito.Mockito.when(jsonNode.get("username")).thenReturn(userNameMock);
        org.mockito.Mockito.when(userNameMock.asText()).thenReturn(" ");
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> errorsOptional = validator.validateCreateRequest(jsonNode);
        junit.framework.TestCase.assertTrue(errorsOptional.isPresent());
        org.hamcrest.MatcherAssert.assertThat(errorsOptional.get().getErrors(), org.hamcrest.core.IsIterableContaining.hasItem("Field [username] is required"));
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnEmpty_ifAUsernameIsPresent() {
        com.fasterxml.jackson.databind.JsonNode jsonNode = org.mockito.Mockito.mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode userNameMock = org.mockito.Mockito.mock(com.fasterxml.jackson.databind.JsonNode.class);
        org.mockito.Mockito.when(jsonNode.get("username")).thenReturn(userNameMock);
        org.mockito.Mockito.when(userNameMock.asText()).thenReturn("a-user-name");
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> errorsOptional = validator.validateCreateRequest(jsonNode);
        junit.framework.TestCase.assertFalse(errorsOptional.isPresent());
    }
}
