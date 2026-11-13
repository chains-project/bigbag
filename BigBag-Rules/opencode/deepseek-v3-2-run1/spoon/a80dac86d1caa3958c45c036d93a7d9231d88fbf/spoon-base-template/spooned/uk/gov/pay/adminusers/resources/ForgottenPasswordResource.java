package uk.gov.pay.adminusers.resources;
@javax.ws.rs.Path(uk.gov.pay.adminusers.resources.ForgottenPasswordResource.FORGOTTEN_PASSWORDS_RESOURCE)
@io.swagger.v3.oas.annotations.tags.Tag(name = "Passwords")
public class ForgottenPasswordResource {
    public static final java.lang.String FORGOTTEN_PASSWORDS_RESOURCE = "/v1/api/forgotten-passwords";

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(uk.gov.pay.adminusers.resources.ForgottenPasswordResource.class);

    private static final int MAX_LENGTH = 255;

    private final uk.gov.pay.adminusers.service.ForgottenPasswordServices forgottenPasswordServices;

    private final uk.gov.pay.adminusers.resources.ForgottenPasswordValidator validator;

    @com.google.inject.Inject
    public ForgottenPasswordResource(uk.gov.pay.adminusers.service.ForgottenPasswordServices forgottenPasswordServices) {
        this.forgottenPasswordServices = forgottenPasswordServices;
        validator = new uk.gov.pay.adminusers.resources.ForgottenPasswordValidator();
    }

    @javax.ws.rs.POST
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(summary = "Create a new forgotten password request (sends email to user)", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(requiredProperties = { "username" }, example = ("{" + "    \"username\": \"a75f2719456c80eb29a10bbf1c92e7b7@example.com\"") + "}"))), responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid payload"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not found") })
    public javax.ws.rs.core.Response sendForgottenPassword(com.fasterxml.jackson.databind.JsonNode payload) {
        uk.gov.pay.adminusers.resources.ForgottenPasswordResource.LOGGER.info("ForgottenPassword CREATE request - [ {} ]", payload);
        java.util.Optional<uk.gov.pay.adminusers.utils.Errors> errorsOptional = validator.validateCreateRequest(payload);
        return errorsOptional.map(errors -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.BAD_REQUEST).type(javax.ws.rs.core.MediaType.APPLICATION_JSON).entity(errors).build()).orElseGet(() -> {
            forgottenPasswordServices.create(payload.get("username").asText());
            return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.OK).build();
        });
    }

    @javax.ws.rs.Path("/{code}")
    @javax.ws.rs.GET
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(summary = "Verify forgotten password code", responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.ForgottenPassword.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not found") })
    public javax.ws.rs.core.Response findNonExpiredForgottenPassword(@io.swagger.v3.oas.annotations.Parameter(example = "bc9039e00cba4e63b2c92ecd0e188aba")
    @javax.ws.rs.PathParam("code")
    java.lang.String code) {
        uk.gov.pay.adminusers.resources.ForgottenPasswordResource.LOGGER.info("ForgottenPassword GET request - [ {} ]", code);
        if (org.apache.commons.lang3.StringUtils.isNotBlank(code) && (code.length() > uk.gov.pay.adminusers.resources.ForgottenPasswordResource.MAX_LENGTH)) {
            return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.NOT_FOUND).build();
        }
        return forgottenPasswordServices.findNonExpired(code).map(forgottenPassword -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.OK).type(javax.ws.rs.core.MediaType.APPLICATION_JSON).entity(forgottenPassword).build()).orElseGet(() -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.NOT_FOUND).build());
    }
}
