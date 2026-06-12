package uk.gov.pay.adminusers.resources;
@javax.ws.rs.Path(uk.gov.pay.adminusers.resources.UserResource.USERS_RESOURCE)
public class UserResource {
    public static final java.lang.String USERS_RESOURCE = "/v1/api/users";

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(uk.gov.pay.adminusers.resources.UserResource.class);

    private static final com.google.common.base.Splitter COMMA_SEPARATOR = com.google.common.base.Splitter.on(',').trimResults();

    public static final java.lang.String CONSTRAINT_VIOLATION_MESSAGE = "ERROR: duplicate key value violates unique constraint";

    private final uk.gov.pay.adminusers.service.UserServices userServices;

    private final uk.gov.pay.adminusers.service.UserServicesFactory userServicesFactory;

    private final uk.gov.pay.adminusers.service.ExistingUserOtpDispatcher existingUserOtpDispatcher;

    private final uk.gov.pay.adminusers.resources.UserRequestValidator validator;

    @com.google.inject.Inject
    public UserResource(uk.gov.pay.adminusers.service.UserServices userServices, uk.gov.pay.adminusers.resources.UserRequestValidator validator, uk.gov.pay.adminusers.service.UserServicesFactory userServicesFactory, uk.gov.pay.adminusers.service.ExistingUserOtpDispatcher existingUserOtpDispatcher) {
        this.userServices = userServices;
        this.validator = validator;
        this.userServicesFactory = userServicesFactory;
        this.existingUserOtpDispatcher = existingUserOtpDispatcher;
    }

    @javax.ws.rs.Path("/find")
    @javax.ws.rs.POST
    @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(tags = "Users", summary = "Find user by username", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(example = ("{" + "    \"username\": \"user@somegovernmentdept.gov.uk\"") + "}"))), responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.User.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid search params"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not found") })
    public javax.ws.rs.core.Response findUser(com.fasterxml.jackson.databind.JsonNode payload) {
        uk.gov.pay.adminusers.resources.UserResource.LOGGER.info("User FIND request");
        return validator.validateFindRequest(payload).map(errors -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.BAD_REQUEST).entity(errors).build()).orElseGet(() -> userServices.findUserByUsername(payload.get(uk.gov.pay.adminusers.model.User.FIELD_USERNAME).asText()).map(user -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.OK).type(javax.ws.rs.core.MediaType.APPLICATION_JSON).entity(user).build()).orElseGet(() -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.NOT_FOUND).build()));
    }

    @javax.ws.rs.Path("/{userExternalId}")
    @javax.ws.rs.GET
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(tags = "Users", summary = "Find user by user external ID", responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.User.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not found") })
    public javax.ws.rs.core.Response getUser(@io.swagger.v3.oas.annotations.Parameter(example = "93ba1ec4ed6a4238a59f16ad97b4fa12")
    @javax.ws.rs.PathParam("userExternalId")
    java.lang.String externalId) {
        uk.gov.pay.adminusers.resources.UserResource.LOGGER.info("User GET request - [ {} ]", externalId);
        return userServices.findUserByExternalId(externalId).map(user -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.OK).type(javax.ws.rs.core.MediaType.APPLICATION_JSON).entity(user).build()).orElseGet(() -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.NOT_FOUND).build());
    }

    @javax.ws.rs.POST
    @javax.ws.rs.Path("/admin-emails-for-gateway-accounts")
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(tags = "Users", summary = "Get admin user emails for given gateway account IDs", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(example = ("{" + "    \"gatewayAccountIds\": [\"1\",\"2\"]") + "}"))), responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(example = (((((("{" + "    \"1\": [") + "        \"user@somegovernmentdept.gov.uk\"") + "    ],") + "    \"2\": [") + "        \"user2@somegovernmentdept.gov.uk\"") + "    ]") + "}"))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Invalid request") })
    public java.util.Map<java.lang.String, java.util.List<java.lang.String>> getAdminUserEmailsForGatewayAccountIds(@javax.validation.Valid
    java.util.Map<java.lang.String, java.util.List<java.lang.String>> gatewayAccountIds) {
        return userServices.getAdminUserEmailsForGatewayAccountIds(gatewayAccountIds.get("gatewayAccountIds"));
    }

    @javax.ws.rs.GET
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(tags = "Users", summary = "Gets users with the associated external ids", responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.User.class)))) })
    public javax.ws.rs.core.Response getUsers(@io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(example = "93ba1ec4ed6a4238a59f16ad97b4fa12,1234"))
    @javax.ws.rs.QueryParam("ids")
    java.lang.String externalIds) {
        uk.gov.pay.adminusers.resources.UserResource.LOGGER.info("Users GET request - [ {} ]", externalIds);
        java.util.List<java.lang.String> externalIdsList = uk.gov.pay.adminusers.resources.UserResource.COMMA_SEPARATOR.splitToList(externalIds);
        java.util.List<uk.gov.pay.adminusers.model.User> users = userServices.findUsersByExternalIds(externalIdsList);
        return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.OK).type(javax.ws.rs.core.MediaType.APPLICATION_JSON).entity(users).build();
    }

    @javax.ws.rs.POST
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(tags = "Users", summary = "Create new user", responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Created", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.User.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid payload"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "User with username already exists"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error") })
    public javax.ws.rs.core.Response createUser(@io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.CreateUserRequest.class))
    com.fasterxml.jackson.databind.JsonNode node) {
        uk.gov.pay.adminusers.resources.UserResource.LOGGER.info("Attempting user create request");
        return validator.validateCreateRequest(node).map(errors -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.BAD_REQUEST).entity(errors).build()).orElseGet(() -> {
            java.lang.String roleName = node.get(uk.gov.pay.adminusers.model.CreateUserRequest.FIELD_ROLE_NAME).asText();
            java.lang.String userName = node.get(uk.gov.pay.adminusers.model.CreateUserRequest.FIELD_USERNAME).asText();
            try {
                uk.gov.pay.adminusers.model.User newUser = userServicesFactory.userCreator().doCreate(uk.gov.pay.adminusers.model.CreateUserRequest.from(node), roleName);
                uk.gov.pay.adminusers.resources.UserResource.LOGGER.info("User created successfully [{}]", newUser.getExternalId());
                return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.CREATED).type(javax.ws.rs.core.MediaType.APPLICATION_JSON).entity(newUser).build();
            } catch (java.lang.Exception e) {
                return handleCreateUserException(userName, e);
            }
        });
    }

    @javax.ws.rs.Path("/authenticate")
    @javax.ws.rs.POST
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(tags = "Users", summary = "Authenticate a given username/password", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(example = "{ \"username\": \"user@somegovernmentdept.gov.uk\"," + "\"password\": \"a-password\"}"))), responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.User.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid payload"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorised") })
    public javax.ws.rs.core.Response authenticate(com.fasterxml.jackson.databind.JsonNode node) {
        uk.gov.pay.adminusers.resources.UserResource.LOGGER.info("User authenticate request");
        return validator.validateAuthenticateRequest(node).map(errors -> javax.ws.rs.core.Response.status(400).entity(errors).build()).orElseGet(() -> {
            java.util.Optional<uk.gov.pay.adminusers.model.User> userOptional = userServices.authenticate(node.get("username").asText(), node.get("password").asText());
            return userOptional.map(user -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.OK).type(javax.ws.rs.core.MediaType.APPLICATION_JSON).entity(user).build()).orElseGet(() -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.UNAUTHORIZED).type(javax.ws.rs.core.MediaType.APPLICATION_JSON).entity(unauthorisedErrorMessage()).build());
        });
    }

    @javax.ws.rs.Path("/{userExternalId}/second-factor")
    @javax.ws.rs.POST
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(tags = "Users", summary = "Send OTP via SMS for an existing user", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(example = "{ \"provisional\": false}"))), responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid payload"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not found") })
    public javax.ws.rs.core.Response sendOtpSms(@io.swagger.v3.oas.annotations.Parameter(example = "93ba1ec4ed6a4238a59f16ad97b4fa12")
    @javax.ws.rs.PathParam("userExternalId")
    java.lang.String externalId, com.fasterxml.jackson.databind.JsonNode payload) {
        uk.gov.pay.adminusers.resources.UserResource.LOGGER.info("User 2FA new passcode request");
        return validator.validateNewSecondFactorPasscodeRequest(payload).map(errors -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.BAD_REQUEST).entity(errors).build()).orElseGet(() -> {
            boolean changingSignInMethod = ((payload != null) && (payload.get("provisional") != null)) && payload.get("provisional").asBoolean();
            if (changingSignInMethod) {
                return existingUserOtpDispatcher.sendChangeSignMethodToSmsOtp(externalId).map(twoFAToken -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.OK).type(javax.ws.rs.core.MediaType.APPLICATION_JSON).build()).orElseGet(() -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.NOT_FOUND).build());
            }
            return existingUserOtpDispatcher.sendSignInOtp(externalId).map(twoFAToken -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.OK).type(javax.ws.rs.core.MediaType.APPLICATION_JSON).build()).orElseGet(() -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.NOT_FOUND).build());
        });
    }

    @javax.ws.rs.Path("/{userExternalId}/second-factor/authenticate")
    @javax.ws.rs.POST
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(tags = "Users", summary = "Authenticate 2FA code", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(requiredProperties = { "code" }, example = "{ \"code\": 123456}"))), responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.User.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid payload"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorised") })
    public javax.ws.rs.core.Response authenticateSecondFactor(@io.swagger.v3.oas.annotations.Parameter(example = "93ba1ec4ed6a4238a59f16ad97b4fa12")
    @javax.ws.rs.PathParam("userExternalId")
    java.lang.String externalId, com.fasterxml.jackson.databind.JsonNode payload) {
        uk.gov.pay.adminusers.resources.UserResource.LOGGER.info("User 2FA authenticate passcode request");
        return validator.validate2FAAuthRequest(payload).map(errors -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.BAD_REQUEST).entity(errors).build()).orElseGet(() -> userServices.authenticateSecondFactor(externalId, payload.get("code").asInt()).map(user -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.OK).type(javax.ws.rs.core.MediaType.APPLICATION_JSON).entity(user).build()).orElseGet(() -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.UNAUTHORIZED).build()));
    }

    @javax.ws.rs.Path("/{userExternalId}/second-factor/provision")
    @javax.ws.rs.POST
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(tags = "Users", summary = "Create a new provisional OTP key for a user", responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.User.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not found") })
    public javax.ws.rs.core.Response newSecondFactorOtpKey(@io.swagger.v3.oas.annotations.Parameter(example = "93ba1ec4ed6a4238a59f16ad97b4fa12")
    @javax.ws.rs.PathParam("userExternalId")
    java.lang.String externalId) {
        uk.gov.pay.adminusers.resources.UserResource.LOGGER.info("User 2FA provision new OTP key request");
        return userServices.provisionNewOtpKey(externalId).map(user -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.OK).type(javax.ws.rs.core.MediaType.APPLICATION_JSON).entity(user).build()).orElseGet(() -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.NOT_FOUND).build());
    }

    @javax.ws.rs.Path("/{userExternalId}/second-factor/activate")
    @javax.ws.rs.POST
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(tags = "Users", summary = "Activate a new OTP key and method for a user", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(requiredProperties = { "second_factor", "code" }, example = "{ \"code\": 123456, \"second_factor\": \"SMS\"}"))), responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.User.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid payload"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorised") })
    public javax.ws.rs.core.Response activateSecondFactorOtpKey(@io.swagger.v3.oas.annotations.Parameter(example = "93ba1ec4ed6a4238a59f16ad97b4fa12")
    @javax.ws.rs.PathParam("userExternalId")
    java.lang.String externalId, com.fasterxml.jackson.databind.JsonNode payload) {
        uk.gov.pay.adminusers.resources.UserResource.LOGGER.info("User 2FA activate new OTP key request");
        return validator.validate2faActivateRequest(payload).map(errors -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.BAD_REQUEST).entity(errors).build()).orElseGet(() -> {
            int code = payload.get("code").asInt();
            uk.gov.pay.adminusers.model.SecondFactorMethod secondFactor = uk.gov.pay.adminusers.model.SecondFactorMethod.valueOf(payload.get("second_factor").asText());
            return userServices.activateNewOtpKey(externalId, secondFactor, code).map(user -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.OK).type(javax.ws.rs.core.MediaType.APPLICATION_JSON).entity(user).build()).orElseGet(() -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.UNAUTHORIZED).build());
        });
    }

    @javax.ws.rs.Path("/{userExternalId}/reset-second-factor")
    @javax.ws.rs.POST
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(tags = "Users", summary = "Reset 2FA to SMS", responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.User.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User with userExternalId not found") })
    public javax.ws.rs.core.Response resetSecondFactor(@io.swagger.v3.oas.annotations.Parameter(example = "93ba1ec4ed6a4238a59f16ad97b4fa12")
    @javax.ws.rs.PathParam("userExternalId")
    java.lang.String externalId) {
        return userServices.resetSecondFactor(externalId).map(user -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.OK).entity(user).build()).orElseGet(() -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.NOT_FOUND).build());
    }

    @io.dropwizard.jersey.PATCH
    @javax.ws.rs.Path("/{userExternalId}")
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(tags = "Users", summary = "Update user attributes", description = "Patch user attributes. <br>" + "Supports patching (replace op) fields `disabled, email, features, telephone_number` and `append` for attrbute `sessionVersion`.", responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.User.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid payload"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User with userExternalId not found") })
    public javax.ws.rs.core.Response updateUserAttribute(@io.swagger.v3.oas.annotations.Parameter(example = "93ba1ec4ed6a4238a59f16ad97b4fa12")
    @javax.ws.rs.PathParam("userExternalId")
    java.lang.String externalId, @io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.PatchRequest.class))
    com.fasterxml.jackson.databind.JsonNode node) {
        uk.gov.pay.adminusers.resources.UserResource.LOGGER.info("User update attribute attempt request");
        return validator.validatePatchRequest(node).map(errors -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.BAD_REQUEST).entity(errors).build()).orElseGet(() -> userServices.patchUser(externalId, uk.gov.pay.adminusers.model.PatchRequest.from(node)).map(user -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.OK).entity(user).build()).orElseGet(() -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.NOT_FOUND).build()));
    }

    @javax.ws.rs.PUT
    @javax.ws.rs.Path("/{userExternalId}/services/{serviceExternalId}")
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(tags = "Users", summary = "Update user's role for a service", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(requiredProperties = { "service_external_id", "role_name" }, example = "{ \"role_name\":\"admin\"}"))), responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.User.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid payload"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not found"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "User does not belong to service") })
    public javax.ws.rs.core.Response updateServiceRole(@io.swagger.v3.oas.annotations.Parameter(example = "93ba1ec4ed6a4238a59f16ad97b4fa12")
    @javax.ws.rs.PathParam("userExternalId")
    java.lang.String userExternalId, @io.swagger.v3.oas.annotations.Parameter(example = "7d19aff33f8948deb97ed16b2912dcd3")
    @javax.ws.rs.PathParam("serviceExternalId")
    java.lang.String serviceExternalId, com.fasterxml.jackson.databind.JsonNode payload) {
        uk.gov.pay.adminusers.resources.UserResource.LOGGER.info("User update service role request");
        return validator.validateServiceRole(payload).map(errors -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.BAD_REQUEST).entity(errors).build()).orElseGet(() -> {
            java.lang.String roleName = payload.get(uk.gov.pay.adminusers.model.User.FIELD_ROLE_NAME).asText();
            return userServicesFactory.serviceRoleUpdater().doUpdate(userExternalId, serviceExternalId, roleName).map(user -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.OK).entity(user).build()).orElseGet(() -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.NOT_FOUND).build());
        });
    }

    @javax.ws.rs.POST
    @javax.ws.rs.Path("/{userExternalId}/services")
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(tags = "Users", summary = "Assign a new service along with role to a user", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(requiredProperties = { "service_external_id", "role_name" }, example = "{ \"service_external_id\":\"7d19aff33f8948deb97ed16b2912dcd3\"," + "\"role_name\":\"admin\"}"))), responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.User.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid payload"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User with userExternalId not found"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "User already got access to service") })
    public javax.ws.rs.core.Response createServiceRole(@io.swagger.v3.oas.annotations.Parameter(example = "93ba1ec4ed6a4238a59f16ad97b4fa12")
    @javax.ws.rs.PathParam("userExternalId")
    java.lang.String userExternalId, com.fasterxml.jackson.databind.JsonNode payload) {
        uk.gov.pay.adminusers.resources.UserResource.LOGGER.info("Assign service role to a user {} request", userExternalId);
        return validator.validateAssignServiceRequest(payload).map(errors -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.BAD_REQUEST).entity(errors).build()).orElseGet(() -> {
            java.lang.String serviceExternalId = payload.get(uk.gov.pay.adminusers.model.User.FIELD_SERVICE_EXTERNAL_ID).asText();
            java.lang.String roleName = payload.get(uk.gov.pay.adminusers.model.User.FIELD_ROLE_NAME).asText();
            return userServicesFactory.serviceRoleCreator().doCreate(userExternalId, serviceExternalId, roleName).map(user -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.OK).entity(user).build()).orElseGet(() -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.NOT_FOUND).build());
        });
    }

    private javax.ws.rs.core.Response handleCreateUserException(java.lang.String userName, java.lang.Exception e) {
        if (e.getMessage().contains(uk.gov.pay.adminusers.resources.UserResource.CONSTRAINT_VIOLATION_MESSAGE)) {
            throw uk.gov.pay.adminusers.service.AdminUsersExceptions.conflictingUsername(userName);
        } else if (e instanceof javax.ws.rs.WebApplicationException) {
            throw ((javax.ws.rs.WebApplicationException) (e));
        } else {
            uk.gov.pay.adminusers.resources.UserResource.LOGGER.error("unknown database error during user creation for user [{}]", userName, e);
            throw uk.gov.pay.adminusers.service.AdminUsersExceptions.internalServerError("unable to create user at this moment");
        }
    }

    private java.util.Map<java.lang.String, java.util.List<java.lang.String>> unauthorisedErrorMessage() {
        return java.util.Map.of("errors", java.util.List.of("invalid username and/or password"));
    }
}
