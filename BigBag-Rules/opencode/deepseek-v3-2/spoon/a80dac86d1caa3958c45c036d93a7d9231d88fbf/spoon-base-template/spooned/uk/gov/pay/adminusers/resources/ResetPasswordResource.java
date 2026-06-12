package uk.gov.pay.adminusers.resources;
@javax.ws.rs.Path("/")
public class ResetPasswordResource {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(uk.gov.pay.adminusers.resources.ResetPasswordResource.class);

    private static final java.lang.String RESET_PASSWORD_RESOURCE = "/v1/api/reset-password";

    /* default */
    static final java.lang.String FIELD_CODE = "forgotten_password_code";

    /* default */
    static final java.lang.String FIELD_PASSWORD = "new_password";

    private final uk.gov.pay.adminusers.resources.ResetPasswordValidator resetPasswordValidator;

    private final uk.gov.pay.adminusers.service.ResetPasswordService resetPasswordService;

    @com.google.inject.Inject
    public ResetPasswordResource(uk.gov.pay.adminusers.resources.ResetPasswordValidator resetPasswordValidator, uk.gov.pay.adminusers.service.ResetPasswordService resetPasswordService) {
        this.resetPasswordValidator = resetPasswordValidator;
        this.resetPasswordService = resetPasswordService;
    }

    @javax.ws.rs.Path(uk.gov.pay.adminusers.resources.ResetPasswordResource.RESET_PASSWORD_RESOURCE)
    @javax.ws.rs.POST
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(tags = { "Passwords" }, summary = "Reset password", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(requiredProperties = { "forgotten_password_code", "new_password" }, example = (("{" + "    \"forgotten_password_code\": \"bc9039e00cba4e63b2c92ecd0e188aba\",") + "    \"new_password\": \"new-password\"") + "}"))), responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Updated password successfully"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid payload"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Expired or non-existent code") })
    public javax.ws.rs.core.Response resetForgottenPassword(com.fasterxml.jackson.databind.JsonNode payload) {
        return resetPasswordValidator.validateResetRequest(payload).map(errors -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.BAD_REQUEST).type(javax.ws.rs.core.MediaType.APPLICATION_JSON).entity(errors).build()).orElseGet(() -> resetPasswordService.updatePassword(payload.get(uk.gov.pay.adminusers.resources.ResetPasswordResource.FIELD_CODE).asText(), payload.get(uk.gov.pay.adminusers.resources.ResetPasswordResource.FIELD_PASSWORD).asText()).map(userId -> {
            uk.gov.pay.adminusers.resources.ResetPasswordResource.LOGGER.info("user ID {} updated password successfully", userId);
            return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.NO_CONTENT).build();
        }).orElseGet(() -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.NOT_FOUND).entity(uk.gov.pay.adminusers.utils.Errors.from(java.util.List.of(java.lang.String.format("Field [%s] non-existent/expired", uk.gov.pay.adminusers.resources.ResetPasswordResource.FIELD_CODE)))).build()));
    }
}
