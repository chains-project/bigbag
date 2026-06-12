package uk.gov.pay.adminusers.resources;
@javax.ws.rs.Path(uk.gov.pay.adminusers.resources.ServiceResource.SERVICES_RESOURCE)
public class ServiceResource {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(uk.gov.pay.adminusers.resources.ServiceResource.class);

    /* default */
    static final java.lang.String HEADER_USER_CONTEXT = "GovUkPay-User-Context";

    public static final java.lang.String SERVICES_RESOURCE = "/v1/api/services";

    public static final java.lang.String FIELD_NAME = "name";

    private final uk.gov.pay.adminusers.persistence.dao.UserDao userDao;

    private final uk.gov.pay.adminusers.persistence.dao.ServiceDao serviceDao;

    private final uk.gov.pay.adminusers.service.LinksBuilder linksBuilder;

    private final uk.gov.pay.adminusers.resources.ServiceRequestValidator serviceRequestValidator;

    private final uk.gov.pay.adminusers.service.ServiceServicesFactory serviceServicesFactory;

    private final uk.gov.pay.adminusers.service.StripeAgreementService stripeAgreementService;

    private final uk.gov.pay.adminusers.resources.GovUkPayAgreementRequestValidator govUkPayAgreementRequestValidator;

    private final uk.gov.pay.adminusers.service.GovUkPayAgreementService govUkPayAgreementService;

    private final uk.gov.pay.adminusers.service.SendLiveAccountCreatedEmailService sendLiveAccountCreatedEmailService;

    @com.google.inject.Inject
    public ServiceResource(uk.gov.pay.adminusers.persistence.dao.UserDao userDao, uk.gov.pay.adminusers.persistence.dao.ServiceDao serviceDao, uk.gov.pay.adminusers.service.LinksBuilder linksBuilder, uk.gov.pay.adminusers.resources.ServiceRequestValidator serviceRequestValidator, uk.gov.pay.adminusers.service.ServiceServicesFactory serviceServicesFactory, uk.gov.pay.adminusers.service.StripeAgreementService stripeAgreementService, uk.gov.pay.adminusers.resources.GovUkPayAgreementRequestValidator govUkPayAgreementRequestValidator, uk.gov.pay.adminusers.service.GovUkPayAgreementService govUkPayAgreementService, uk.gov.pay.adminusers.service.SendLiveAccountCreatedEmailService sendLiveAccountCreatedEmailService) {
        this.userDao = userDao;
        this.serviceDao = serviceDao;
        this.linksBuilder = linksBuilder;
        this.serviceRequestValidator = serviceRequestValidator;
        this.serviceServicesFactory = serviceServicesFactory;
        this.stripeAgreementService = stripeAgreementService;
        this.govUkPayAgreementRequestValidator = govUkPayAgreementRequestValidator;
        this.govUkPayAgreementService = govUkPayAgreementService;
        this.sendLiveAccountCreatedEmailService = sendLiveAccountCreatedEmailService;
    }

    @javax.ws.rs.GET
    @javax.ws.rs.Path("/list")
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(summary = "Get all services", tags = "Services", responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.Service.class)))) })
    public javax.ws.rs.core.Response getServices() {
        uk.gov.pay.adminusers.resources.ServiceResource.LOGGER.info("Get Services request");
        java.util.List<uk.gov.pay.adminusers.model.Service> services = serviceDao.listAll().stream().map(uk.gov.pay.adminusers.persistence.entity.ServiceEntity::toService).map(linksBuilder::decorate).collect(java.util.stream.Collectors.toUnmodifiableList());
        return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.OK).entity(services).build();
    }

    @javax.ws.rs.GET
    @javax.ws.rs.Path("/{serviceExternalId}")
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(tags = "Services", summary = "Find service by external ID", responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.Service.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not found") })
    public javax.ws.rs.core.Response findService(@io.swagger.v3.oas.annotations.Parameter(example = "7d19aff33f8948deb97ed16b2912dcd3")
    @javax.ws.rs.PathParam("serviceExternalId")
    java.lang.String serviceExternalId) {
        uk.gov.pay.adminusers.resources.ServiceResource.LOGGER.info("Find Service request - [ {} ]", serviceExternalId);
        return serviceDao.findByExternalId(serviceExternalId).map(serviceEntity -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.OK).entity(linksBuilder.decorate(serviceEntity.toService())).build()).orElseGet(() -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.NOT_FOUND).build());
    }

    @javax.ws.rs.GET
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(tags = "Services", summary = "Find service associated with gateway account ID", responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.Service.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Missing gateway account ID"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not found") })
    public javax.ws.rs.core.Response findServices(@io.swagger.v3.oas.annotations.Parameter(example = "1")
    @javax.ws.rs.QueryParam("gatewayAccountId")
    java.lang.String gatewayAccountId) {
        uk.gov.pay.adminusers.resources.ServiceResource.LOGGER.info("Find service by gateway account id request - [ {} ]", gatewayAccountId);
        return serviceRequestValidator.validateFindRequest(gatewayAccountId).map(errors -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.BAD_REQUEST).entity(errors).build()).orElseGet(() -> serviceServicesFactory.serviceFinder().byGatewayAccountId(gatewayAccountId).map(service -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.OK).entity(service).build()).orElseGet(() -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.NOT_FOUND).build()));
    }

    @javax.ws.rs.POST
    @javax.ws.rs.Path("/search")
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(tags = "Toolbox", summary = "Search services by name or merchant name", description = "This endpoint returns a list of services using lexical meaning to determine a match to the search criteria", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(example = (("{" + "    \"service_name\": \"service name\",") + "    \"service_merchant_name\": \"service merchant name\"") + "}"))), responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.Service.class)))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid JSON payload") })
    public javax.ws.rs.core.Response searchServices(com.fasterxml.jackson.databind.JsonNode payload) {
        uk.gov.pay.adminusers.resources.ServiceResource.LOGGER.info("Search services request = [ {} ]", payload);
        var searchRequest = uk.gov.pay.adminusers.model.ServiceSearchRequest.from(payload);
        return serviceRequestValidator.validateSearchRequest(searchRequest).map(errors -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.BAD_REQUEST).entity(errors).build()).orElseGet(() -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.OK).entity(serviceServicesFactory.serviceFinder().bySearchRequest(searchRequest)).build());
    }

    @javax.ws.rs.POST
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(tags = "Services", summary = "Create new service", description = "This endpoint creates a new service. And assigns to gateway account ids (Optional). <br> `service_name` keys are supported ISO-639-1 language codes and values are translated service names | key must be `\"en\"` or `\"cy\"`", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(example = ((((("{" + "    \"gateway_account_ids\": [\"1\"],") + "    \"service_name\": {") + "      \"en\": \"Some service name\",") + "      \"cy\": \"Service name in welsh\"") + "    }") + "}"))), responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Created", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.Service.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Gateway account IDs provided has already been assigned to another service"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Invalid JSON payload") })
    public javax.ws.rs.core.Response createService(com.fasterxml.jackson.databind.JsonNode payload) {
        uk.gov.pay.adminusers.resources.ServiceResource.LOGGER.info("Create Service POST request - [ {} ]", payload);
        java.util.List<java.lang.String> gatewayAccountIds = extractGatewayAccountIds(payload);
        java.util.Map<uk.gov.service.payments.commons.model.SupportedLanguage, java.lang.String> serviceNameVariants = getServiceNameVariants(payload);
        uk.gov.pay.adminusers.model.Service service = serviceServicesFactory.serviceCreator().doCreate(gatewayAccountIds, serviceNameVariants);
        return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.CREATED).entity(service).build();
    }

    private java.util.List<java.lang.String> extractGatewayAccountIds(com.fasterxml.jackson.databind.JsonNode payload) {
        java.util.List<java.lang.String> gatewayAccountIds = new java.util.ArrayList<>();
        if ((payload != null) && (payload.get(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_GATEWAY_ACCOUNT_IDS) != null)) {
            payload.get(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_GATEWAY_ACCOUNT_IDS).elements().forEachRemaining(node -> gatewayAccountIds.add(node.textValue()));
        }
        return java.util.List.copyOf(gatewayAccountIds);
    }

    @javax.ws.rs.Path("/{serviceExternalId}")
    @io.dropwizard.jersey.PATCH
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(tags = "Services", summary = "Patch service attributes", description = ((((((((((((((((((((((((((((((("Allows patching below service attributes. Each attribute has its own validation depending on data type." + " Request can either be a single object or an array of objects. It’s similar to (but not 100% compliant with) [JSON Patch](http://jsonpatch.com/).") + "<br>\n ") + " ") + "| op |  field | example |\n") + "| --- |  --- | ----|\n") + "| add | gateway_account_ids  | [\"1\"] |\n") + "| replace | redirect_to_service_immediately_on_terminal_state | false |\n") + "| replace | experimental_features_enabled | false |\n") + "| replace | agent_initiated_moto_enabled | false |\n") + "| replace | collect_billing_address | true |\n") + "| replace | current_go_live_stage | NOT_STARTED |\n") + "| replace | current_psp_test_account_stage | NOT_STARTED |\n") + "| replace | merchant_details/name, organisatio | name |\n") + "| replace | merchant_details/address_line1, Address lin | 1 |\n") + "| replace | merchant_details/address_line2, Address lin | 2  |\n") + "| replace | merchant_details/address_city | London |\n") + "| replace | merchant_details/address_country | GB |\n") + "| replace | merchant_details/address_postcode | E6 8XX |\n") + "| replace | merchant_detail |/email,  |\n") + "| replace | merchant_details/email, email@exampl |.com |\n") + "| replace | merchant_detail |/url,  |\n") + "| replace | merchant_details/url, http://www.exampl |.org |\n") + "| replace | merchant_detail |/telephone_number,  |\n") + "| replace | merchant_details/telephone_number | 447700900000 |\n") + "| replace | custom_branding | { \"css_url\": \"css url\", \"image_url\": \"image url\"} |\n") + "| replace | custom_branding | {} |\n") + "| replace | service_name/en | Some service name |\n") + "| replace | sector | local government |\n") + "| replace | internal | true |\n") + "| replace | archived | true |\n") + "| replace | went_live_date | 2022-04-09T18:07:46Z |\n") + "| replace | default_billing_address_country | GB | ", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @io.swagger.v3.oas.annotations.media.Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.ServiceUpdateRequest.class)))), responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.Service.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid payload") })
    public javax.ws.rs.core.Response updateServiceAttribute(@io.swagger.v3.oas.annotations.Parameter(example = "7d19aff33f8948deb97ed16b2912dcd3")
    @javax.ws.rs.PathParam("serviceExternalId")
    java.lang.String serviceExternalId, com.fasterxml.jackson.databind.JsonNode payload) {
        uk.gov.pay.adminusers.resources.ServiceResource.LOGGER.info("Service PATCH request - [ {} ]", serviceExternalId);
        return serviceRequestValidator.validateUpdateAttributeRequest(payload).map(errors -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.BAD_REQUEST).entity(errors).build()).orElseGet(() -> processUpdateServiceAttributePayload(serviceExternalId, payload));
    }

    private javax.ws.rs.core.Response processUpdateServiceAttributePayload(java.lang.String serviceExternalId, com.fasterxml.jackson.databind.JsonNode payload) {
        final java.util.List<uk.gov.pay.adminusers.model.ServiceUpdateRequest> requests = uk.gov.pay.adminusers.model.ServiceUpdateRequest.getUpdateRequests(payload);
        return serviceServicesFactory.serviceUpdater().doUpdate(serviceExternalId, requests).map(service -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.OK).entity(service).build()).orElseGet(() -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.NOT_FOUND).build());
    }

    @javax.ws.rs.Path("/{serviceExternalId}/merchant-details")
    @javax.ws.rs.PUT
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(tags = "Services", summary = "Update merchant details of a service", responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.Service.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid payload") })
    public javax.ws.rs.core.Response updateServiceMerchantDetails(@io.swagger.v3.oas.annotations.Parameter(example = "7d19aff33f8948deb97ed16b2912dcd3")
    @javax.ws.rs.PathParam("serviceExternalId")
    java.lang.String serviceExternalId, @io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.UpdateMerchantDetailsRequest.class))
    com.fasterxml.jackson.databind.JsonNode payload) throws uk.gov.pay.adminusers.exception.ValidationException, uk.gov.pay.adminusers.exception.ServiceNotFoundException {
        uk.gov.pay.adminusers.resources.ServiceResource.LOGGER.info("Service PUT request to update merchant details - [ {} ]", serviceExternalId);
        serviceRequestValidator.validateUpdateMerchantDetailsRequest(payload);
        uk.gov.pay.adminusers.model.Service service = serviceServicesFactory.serviceUpdater().doUpdateMerchantDetails(serviceExternalId, uk.gov.pay.adminusers.model.UpdateMerchantDetailsRequest.from(payload));
        return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.OK).entity(service).build();
    }

    @javax.ws.rs.Path("/{serviceExternalId}/users")
    @javax.ws.rs.GET
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(tags = "Services", summary = "Find users of a service", responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.User.class)))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not found") })
    public javax.ws.rs.core.Response findUsersByServiceId(@javax.ws.rs.PathParam("serviceExternalId")
    java.lang.String serviceExternalId) {
        uk.gov.pay.adminusers.resources.ServiceResource.LOGGER.info("Service users GET request - [ {} ]", serviceExternalId);
        return serviceDao.findByExternalId(serviceExternalId).map(serviceEntity -> javax.ws.rs.core.Response.status(200).entity(userDao.findByServiceId(serviceEntity.getId()).stream().map(uk.gov.pay.adminusers.persistence.entity.UserEntity::toUser).map(linksBuilder::decorate).collect(java.util.stream.Collectors.toUnmodifiableList())).build()).orElseGet(() -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.NOT_FOUND).build());
    }

    // To consider for all the operations add @HeaderParam("GovUkPay-User-Context") and creating a filter
    // so we could map permissions with Regex URLs and Http method passed on to this filter.
    @javax.ws.rs.Path("/{serviceExternalId}/users/{userExternalId}")
    @javax.ws.rs.DELETE
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(tags = "Services", summary = "Delete user from a service", responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "OK"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden. `GovUkPay-User-Context` header is blank"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Conflict. `GovUkPay-User-Context` is same as userExternalId or user with `userExternalId` is not admin of the service") })
    public javax.ws.rs.core.Response removeUserFromService(@io.swagger.v3.oas.annotations.Parameter(example = "7d19aff33f8948deb97ed16b2912dcd3")
    @javax.ws.rs.PathParam("serviceExternalId")
    java.lang.String serviceExternalId, @io.swagger.v3.oas.annotations.Parameter(example = "0ddf69c1ba924deca07f0ee748ff1533", description = "Admin user external ID of the service")
    @javax.ws.rs.PathParam("userExternalId")
    java.lang.String userExternalId, @io.swagger.v3.oas.annotations.Parameter(example = "d012mkldfdfnsdhqha7f0ee748ff1546", required = true, description = "User external ID to remove from service")
    @javax.ws.rs.HeaderParam(uk.gov.pay.adminusers.resources.ServiceResource.HEADER_USER_CONTEXT)
    java.lang.String userContext) {
        uk.gov.pay.adminusers.resources.ServiceResource.LOGGER.info("Service users DELETE request - serviceExternalId={}, userExternalId={}", serviceExternalId, userExternalId);
        if (org.apache.commons.lang3.StringUtils.isBlank(userContext)) {
            return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.FORBIDDEN).build();
        } else if (userExternalId.equals(userContext)) {
            uk.gov.pay.adminusers.resources.ServiceResource.LOGGER.info("Failed Service users DELETE request. User and Remover cannot be the same - " + "serviceExternalId={}, removerExternalId={}, userExternalId={}", serviceExternalId, userContext, userExternalId);
            return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.CONFLICT).build();
        }
        serviceServicesFactory.serviceUserRemover().remove(userExternalId, userContext, serviceExternalId);
        uk.gov.pay.adminusers.resources.ServiceResource.LOGGER.info("Succeeded Service users DELETE request - serviceExternalId={}, removerExternalId={}, userExternalId={}", serviceExternalId, userContext, userExternalId);
        return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.NO_CONTENT).build();
    }

    @javax.ws.rs.Path("/{serviceExternalId}/stripe-agreement")
    @javax.ws.rs.POST
    @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(tags = "Services", summary = "Record acceptance of Stripe terms", description = "Records that a GOV.UK Pay agreement has been accepted for the service.", responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Created"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Conflict. Another stripe agreement already exists for the service"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Invalid JSON payload or IP address") })
    public javax.ws.rs.core.Response createStripeAgreement(@io.swagger.v3.oas.annotations.Parameter(example = "7d19aff33f8948deb97ed16b2912dcd3")
    @javax.ws.rs.PathParam("serviceExternalId")
    java.lang.String serviceExternalId, @javax.validation.constraints.NotNull
    @javax.validation.Valid
    uk.gov.pay.adminusers.model.StripeAgreementRequest stripeAgreementRequest) throws java.net.UnknownHostException {
        uk.gov.pay.adminusers.resources.ServiceResource.LOGGER.info("Create stripe agreement POST request - [ {} ]", stripeAgreementRequest.toString());
        stripeAgreementService.doCreate(serviceExternalId, java.net.InetAddress.getByName(stripeAgreementRequest.getIpAddress()));
        return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.CREATED).build();
    }

    @javax.ws.rs.Path("/{serviceExternalId}/stripe-agreement")
    @javax.ws.rs.GET
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(tags = "Services", summary = "Get details about the acceptance of Stripe terms", description = "Retrieves the IP address and timestamp that the Stripe terms were accepted on for the service.", responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.StripeAgreement.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not found") })
    public uk.gov.pay.adminusers.model.StripeAgreement getStripeAgreement(@io.swagger.v3.oas.annotations.Parameter(example = "7d19aff33f8948deb97ed16b2912dcd3")
    @javax.ws.rs.PathParam("serviceExternalId")
    java.lang.String serviceExternalId) {
        return stripeAgreementService.findStripeAgreementByServiceId(serviceExternalId).orElseThrow(() -> new javax.ws.rs.WebApplicationException(javax.ws.rs.core.Response.Status.NOT_FOUND));
    }

    @javax.ws.rs.Path("/{serviceExternalId}/govuk-pay-agreement")
    @javax.ws.rs.POST
    @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(tags = "Services", summary = "Record acceptance of GOV.UK Pay terms", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(requiredProperties = "user_external_id", example = ("{" + "    \"user_external_id\": \"12e3eccfab284ae5bc1108e9c0456ba7\"") + "}"))), responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Created", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.GovUkPayAgreement.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid payload"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Service with serviceExternalId not found"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Agreement already exists for the service.") })
    public javax.ws.rs.core.Response createGovUkPayAgreement(@io.swagger.v3.oas.annotations.Parameter(example = "7d19aff33f8948deb97ed16b2912dcd3")
    @javax.ws.rs.PathParam("serviceExternalId")
    java.lang.String serviceExternalId, com.fasterxml.jackson.databind.JsonNode payload) {
        return govUkPayAgreementRequestValidator.validateCreateRequest(payload).map(errors -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.BAD_REQUEST).entity(errors).build()).orElseGet(() -> createGovUkPayAgreementFromPayload(serviceExternalId, payload));
    }

    @javax.ws.rs.Path("/{serviceExternalId}/govuk-pay-agreement")
    @javax.ws.rs.GET
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(tags = "Services", summary = "Get details about the acceptance of GOV.UK Pay terms", description = "Retrieves the user's email address and timestamp that the GOV.UK Pay terms were accepted on for the service.", responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = uk.gov.pay.adminusers.model.GovUkPayAgreement.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Service with serviceExternalId not found") })
    public javax.ws.rs.core.Response getGovUkPayAgreement(@io.swagger.v3.oas.annotations.Parameter(example = "7d19aff33f8948deb97ed16b2912dcd3")
    @javax.ws.rs.PathParam("serviceExternalId")
    java.lang.String serviceExternalId) {
        return govUkPayAgreementService.findGovUkPayAgreementByServiceId(serviceExternalId).map(agreement -> javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.OK).entity(agreement).build()).orElseThrow(() -> new javax.ws.rs.WebApplicationException(javax.ws.rs.core.Response.Status.NOT_FOUND));
    }

    private javax.ws.rs.core.Response createGovUkPayAgreementFromPayload(java.lang.String serviceExternalId, com.fasterxml.jackson.databind.JsonNode payload) {
        if (govUkPayAgreementService.findGovUkPayAgreementByServiceId(serviceExternalId).isPresent()) {
            return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.CONFLICT).entity(uk.gov.pay.adminusers.utils.Errors.from("GOV.UK Pay agreement information is already stored for this service")).build();
        }
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = serviceDao.findByExternalId(serviceExternalId).orElseThrow(() -> new javax.ws.rs.WebApplicationException(javax.ws.rs.core.Response.Status.NOT_FOUND));
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.UserEntity> userEntity = userDao.findByExternalId(payload.get("user_external_id").asText());
        if (!userEntity.isPresent()) {
            return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.BAD_REQUEST).entity(uk.gov.pay.adminusers.utils.Errors.from("Field [user_external_id] must be a valid user ID")).build();
        }
        if (!userEntity.get().getServicesRole(serviceExternalId).isPresent()) {
            return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.BAD_REQUEST).entity(uk.gov.pay.adminusers.utils.Errors.from("User does not belong to the given service")).build();
        }
        uk.gov.pay.adminusers.model.GovUkPayAgreement govUkPayAgreement = govUkPayAgreementService.doCreate(serviceEntity, userEntity.get().getEmail(), java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC));
        return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.CREATED).entity(govUkPayAgreement).build();
    }

    @javax.ws.rs.Path("/{serviceExternalId}/send-live-email")
    @javax.ws.rs.POST
    @javax.ws.rs.Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @io.swagger.v3.oas.annotations.Operation(tags = "Services", summary = "Sends an email to the user who signed the service agreement to inform them that their service is live", description = "This endpoint will send an email to the user who signed the agreement with GOV.UK Pay for the service informing them that their service is now live." + "The email address used is the email address of the user provided to the [POST /v1/api/services/`{serviceExternalId}`/govuk-pay-agreement] endpoint.", responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Service with serviceExternalId not found") })
    public javax.ws.rs.core.Response sendLiveAccountCreatedEmail(@io.swagger.v3.oas.annotations.Parameter(example = "7d19aff33f8948deb97ed16b2912dcd3")
    @javax.ws.rs.PathParam("serviceExternalId")
    java.lang.String serviceExternalId) {
        serviceDao.findByExternalId(serviceExternalId).orElseThrow(() -> new javax.ws.rs.WebApplicationException(javax.ws.rs.core.Response.Status.NOT_FOUND));
        sendLiveAccountCreatedEmailService.sendEmail(serviceExternalId);
        return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.OK).build();
    }

    private java.util.Map<uk.gov.service.payments.commons.model.SupportedLanguage, java.lang.String> getServiceNameVariants(com.fasterxml.jackson.databind.JsonNode payload) {
        if (payload.hasNonNull("service_name")) {
            com.fasterxml.jackson.databind.JsonNode serviceName = payload.get("service_name");
            return java.util.stream.Stream.of(uk.gov.service.payments.commons.model.SupportedLanguage.values()).filter(supportedLanguage -> serviceName.hasNonNull(supportedLanguage.toString())).collect(java.util.stream.Collectors.toUnmodifiableMap(supportedLanguage -> supportedLanguage, supportedLanguage -> serviceName.get(supportedLanguage.toString()).asText()));
        }
        return java.util.Collections.emptyMap();
    }
}
