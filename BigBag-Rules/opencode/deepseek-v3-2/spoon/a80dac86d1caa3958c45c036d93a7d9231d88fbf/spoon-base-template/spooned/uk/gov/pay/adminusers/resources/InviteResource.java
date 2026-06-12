package uk.gov.pay.adminusers.resources;
@javax.ws.rs.Path(uk.gov.pay.adminusers.resources.InviteResource.INVITES_RESOURCE)
@io.swagger.v3.oas.annotations.tags.Tag(name = "Invites")
public class InviteResource {
    public static final java.lang.String INVITES_RESOURCE = "/v1/api/invites";

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(uk.gov.pay.adminusers.resources.InviteResource.class);

    private static final int MAX_LENGTH_CODE = 255;

    private final uk.gov.pay.adminusers.service.InviteService inviteService;

    private final uk.gov.pay.adminusers.resources.InviteRequestValidator inviteValidator;

    private final uk.gov.pay.adminusers.service.InviteServiceFactory inviteServiceFactory;

    @com.google.inject.Inject
    public InviteResource(uk.gov.pay.adminusers.service.InviteService service, uk.gov.pay.adminusers.resources.InviteRequestValidator inviteValidator, uk.gov.pay.adminusers.service.InviteServiceFactory inviteServiceFactory) {
        inviteService = service;
        this.inviteServiceFactory = inviteServiceFactory;
        this.inviteValidator = inviteValidator;
    }

    @javax.ws.rs.GET
    @javax.ws.rs.Path("/{code}")
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(summary = "Find invite for invite code", responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.Invite.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not found") })
    public javax.ws.rs.core.Response getInvite(@io.swagger.v3.oas.annotations.Parameter(example = "d02jddeib0lqpsir28fbskg9v0rv")
    @javax.ws.rs.PathParam("code")
    java.lang.String code) {
        uk.gov.pay.adminusers.resources.InviteResource.LOGGER.info("Invite GET request for code - [ {} ]", code);
        if (org.apache.commons.lang3.StringUtils.isNotBlank(code) && (code.length() > uk.gov.pay.adminusers.resources.InviteResource.MAX_LENGTH_CODE)) {
            return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.NOT_FOUND).build();
        }
        return inviteServiceFactory.inviteFinder().find(code).map(invite -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.OK).type(javax.ws.rs.core.MediaType.APPLICATION_JSON).entity(invite).build()).orElseGet(() -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.NOT_FOUND).build());
    }

    @javax.ws.rs.POST
    @javax.ws.rs.Path("/{code}/complete")
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(summary = "Completes the invite by creating user/service and invalidating the invite code", description = ("In the case of a user invite, this resource will assign the new service to the existing user and disables the invite. <br>" + "In the case of a service invite, this resource will create a new service, assign gateway account ids (if provided) and also creates a new user and assign to the service<br>") + "The response contains the user and the service id's affected as part of the invite completion in addition to the invite", responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.InviteCompleteResponse.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not found") })
    public javax.ws.rs.core.Response completeInvite(@io.swagger.v3.oas.annotations.Parameter(example = "d02jddeib0lqpsir28fbskg9v0rv")
    @javax.ws.rs.PathParam("code")
    java.lang.String inviteCode, @io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.InviteCompleteRequest.class))
    com.fasterxml.jackson.databind.JsonNode payload) {
        uk.gov.pay.adminusers.resources.InviteResource.LOGGER.info("Invite  complete POST request for code - [ {} ]", inviteCode);
        if (org.apache.commons.lang3.StringUtils.isNotBlank(inviteCode) && (inviteCode.length() > uk.gov.pay.adminusers.resources.InviteResource.MAX_LENGTH_CODE)) {
            return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.NOT_FOUND).build();
        }
        return inviteServiceFactory.inviteCompleteRouter().routeComplete(inviteCode).map(inviteCompleterAndValidate -> {
            uk.gov.pay.adminusers.service.InviteCompleter inviteCompleter = inviteCompleterAndValidate.getLeft();
            return inviteCompleter.withData(inviteCompleteRequestFrom(payload)).complete(inviteCode).map(inviteCompleteResponse -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.OK).entity(inviteCompleteResponse).build()).orElseGet(() -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.NOT_FOUND).build());
        }).orElseGet(() -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.NOT_FOUND).build());
    }

    private uk.gov.pay.adminusers.model.InviteCompleteRequest inviteCompleteRequestFrom(com.fasterxml.jackson.databind.JsonNode payload) {
        uk.gov.pay.adminusers.model.InviteCompleteRequest inviteCompleteRequest = new uk.gov.pay.adminusers.model.InviteCompleteRequest();
        if ((payload != null) && (payload.get(uk.gov.pay.adminusers.model.InviteCompleteRequest.FIELD_GATEWAY_ACCOUNT_IDS) != null)) {
            java.util.List<java.lang.String> gatewayAccountIds = new java.util.ArrayList<>();
            payload.get(uk.gov.pay.adminusers.model.InviteCompleteRequest.FIELD_GATEWAY_ACCOUNT_IDS).elements().forEachRemaining(node -> gatewayAccountIds.add(node.textValue()));
            inviteCompleteRequest.setGatewayAccountIds(gatewayAccountIds);
        }
        return inviteCompleteRequest;
    }

    @javax.ws.rs.POST
    @javax.ws.rs.Path("{code}/otp/generate")
    @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(summary = "Generates and sends otp verification code to the phone number registered in the invite.", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(example = (("{" + " \"telephone_number\": \"07451234567\",") + " \"password\": \"a-password\"") + "}", requiredProperties = { "telephone_number", "password" }))), responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid payload"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not found") })
    public javax.ws.rs.core.Response generateAndDispatchOtp(@io.swagger.v3.oas.annotations.Parameter(example = "d02jddeib0lqpsir28fbskg9v0rv")
    @javax.ws.rs.PathParam("code")
    java.lang.String inviteCode, com.fasterxml.jackson.databind.JsonNode payload) {
        uk.gov.pay.adminusers.resources.InviteResource.LOGGER.info("Invite POST request for generating otp");
        if (org.apache.commons.lang3.StringUtils.isNotBlank(inviteCode) && (inviteCode.length() > uk.gov.pay.adminusers.resources.InviteResource.MAX_LENGTH_CODE)) {
            return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.NOT_FOUND).build();
        }
        return inviteServiceFactory.inviteOtpRouter().routeOtpDispatch(inviteCode).map(inviteOtpDispatcherValidate -> {
            if (inviteOtpDispatcherValidate.getRight()) {
                java.util.Optional<uk.gov.pay.adminusers.utils.Errors> errors = inviteValidator.validateGenerateOtpRequest(payload);
                if (errors.isPresent()) {
                    return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.BAD_REQUEST).entity(errors).build();
                }
            }
            uk.gov.pay.adminusers.service.InviteOtpDispatcher otpDispatcher = inviteOtpDispatcherValidate.getLeft();
            if (otpDispatcher.withData(uk.gov.pay.adminusers.model.InviteOtpRequest.from(payload)).dispatchOtp(inviteCode)) {
                return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.OK).build();
            } else {
                throw uk.gov.pay.adminusers.service.AdminUsersExceptions.internalServerError("unable to dispatch otp at this moment");
            }
        }).orElseGet(() -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.NOT_FOUND).build());
    }

    @javax.ws.rs.GET
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(summary = "List invites for a service", responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.Invite.class)))) })
    public javax.ws.rs.core.Response getInvites(@io.swagger.v3.oas.annotations.Parameter(example = "ahq8745yq387")
    @javax.ws.rs.QueryParam("serviceId")
    java.lang.String serviceId) {
        uk.gov.pay.adminusers.resources.InviteResource.LOGGER.info("List invites GET request for service - [ {} ]", serviceId);
        java.util.List<uk.gov.pay.adminusers.model.Invite> invites = inviteServiceFactory.inviteFinder().findAllActiveInvites(serviceId);
        return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.OK).type(javax.ws.rs.core.MediaType.APPLICATION_JSON).entity(invites).build();
    }

    @javax.ws.rs.POST
    @javax.ws.rs.Path("/service")
    @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(summary = "Creates an invitation to allow self provisioning new service with Pay", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(example = ((("{" + "\"telephone_number\":\"+440787654534\",") + "\"email\": \"example@example.gov.uk\",") + "\"password\" : \"plain-txt-passsword\"") + "}", requiredProperties = { "telephone_number", "email", "password" }))), responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Created", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.Invite.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid payload") })
    public javax.ws.rs.core.Response createServiceInvite(com.fasterxml.jackson.databind.JsonNode payload) {
        uk.gov.pay.adminusers.resources.InviteResource.LOGGER.info("Initiating create service invitation request");
        return inviteValidator.validateCreateServiceRequest(payload).map(errors -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.BAD_REQUEST).entity(errors).build()).orElseGet(() -> {
            uk.gov.pay.adminusers.model.Invite invite = inviteServiceFactory.serviceInvite().doInvite(uk.gov.pay.adminusers.model.InviteServiceRequest.from(payload));
            return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.CREATED).entity(invite).build();
        });
    }

    @javax.ws.rs.POST
    @javax.ws.rs.Path("/user")
    @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(summary = "Creates an invitation to allow a new team member to join an existing service.", responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Created", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.Invite.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid payload"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not found") })
    public javax.ws.rs.core.Response createUserInvite(@io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.InviteUserRequest.class))
    com.fasterxml.jackson.databind.JsonNode payload) {
        uk.gov.pay.adminusers.resources.InviteResource.LOGGER.info("Initiating user invitation request");
        return inviteValidator.validateCreateUserRequest(payload).map(errors -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.BAD_REQUEST).entity(errors).build()).orElseGet(() -> inviteServiceFactory.userInvite().doInvite(uk.gov.pay.adminusers.model.InviteUserRequest.from(payload)).map(invite -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.CREATED).entity(invite).build()).orElseGet(() -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.NOT_FOUND).entity(org.apache.commons.lang3.StringUtils.EMPTY).build()));
    }

    @javax.ws.rs.POST
    @javax.ws.rs.Path("/otp/resend")
    @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(summary = "Resend OTP", responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid payload") })
    public javax.ws.rs.core.Response resendOtp(@io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.InviteOtpRequest.class))
    com.fasterxml.jackson.databind.JsonNode payload) {
        uk.gov.pay.adminusers.resources.InviteResource.LOGGER.info("Invite POST request for resending otp");
        return inviteValidator.validateResendOtpRequest(payload).map(errors -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.BAD_REQUEST).entity(errors).build()).orElseGet(() -> {
            inviteService.reGenerateOtp(uk.gov.pay.adminusers.model.InviteOtpRequest.from(payload));
            return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.OK).build();
        });
    }

    @javax.ws.rs.POST
    @javax.ws.rs.Path("/otp/validate")
    @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(summary = "Validates OTP for the invite and creates user", responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid payload") })
    public javax.ws.rs.core.Response createUserUponOtpValidation(@io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.InviteValidateOtpRequest.class))
    com.fasterxml.jackson.databind.JsonNode payload) {
        uk.gov.pay.adminusers.resources.InviteResource.LOGGER.info("Invite POST request for validating otp and creating user");
        return inviteValidator.validateOtpValidationRequest(payload).map(errors -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.BAD_REQUEST).entity(errors).build()).orElseGet(() -> {
            uk.gov.pay.adminusers.service.ValidateOtpAndCreateUserResult validateOtpAndCreateUserResult = inviteService.validateOtpAndCreateUser(uk.gov.pay.adminusers.model.InviteValidateOtpRequest.from(payload));
            if (!validateOtpAndCreateUserResult.isError()) {
                uk.gov.pay.adminusers.model.User createdUser = validateOtpAndCreateUserResult.getUser();
                java.lang.String serviceIds = createdUser.getServiceRoles().stream().map(serviceRole -> serviceRole.getService().getExternalId()).collect(java.util.stream.Collectors.joining(", "));
                uk.gov.pay.adminusers.resources.InviteResource.LOGGER.info("User created successfully from invitation [{}] for services [{}]", createdUser.getExternalId(), serviceIds);
                return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.CREATED).type(javax.ws.rs.core.MediaType.APPLICATION_JSON).entity(createdUser).build();
            }
            return handleValidateOtpAndCreateUserException(validateOtpAndCreateUserResult.getError());
        });
    }

    @javax.ws.rs.POST
    @javax.ws.rs.Path("/otp/validate/service")
    @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(summary = "Validates OTP for the invite - part of creating service", responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid payload") })
    public javax.ws.rs.core.Response validateOtpKeyForService(@io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.InviteValidateOtpRequest.class))
    com.fasterxml.jackson.databind.JsonNode payload) {
        uk.gov.pay.adminusers.resources.InviteResource.LOGGER.info("Invite POST request for validating otp for service create");
        return inviteValidator.validateOtpValidationRequest(payload).map(errors -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.BAD_REQUEST).entity(errors).build()).orElseGet(() -> inviteService.validateOtp(uk.gov.pay.adminusers.model.InviteValidateOtpRequest.from(payload)).map(this::handleValidateOtpAndCreateUserException).orElseGet(() -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.OK).build()));
    }

    private javax.ws.rs.core.Response handleValidateOtpAndCreateUserException(javax.ws.rs.WebApplicationException error) {
        throw error;
    }
}
