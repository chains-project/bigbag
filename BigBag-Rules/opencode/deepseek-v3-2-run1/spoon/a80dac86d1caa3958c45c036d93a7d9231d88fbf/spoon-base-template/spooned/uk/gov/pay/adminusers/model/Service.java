package uk.gov.pay.adminusers.model;
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
@com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Service {
    public static final java.lang.String DEFAULT_NAME_VALUE = "System Generated";

    private java.lang.Integer id;

    @io.swagger.v3.oas.annotations.media.Schema(example = "7d19aff33f8948deb97ed16b2912dcd3")
    private java.lang.String externalId;

    private java.util.List<uk.gov.pay.adminusers.model.Link> links = new java.util.ArrayList<>();

    @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @io.swagger.v3.oas.annotations.media.Schema(example = "1"))
    private java.util.List<java.lang.String> gatewayAccountIds = new java.util.ArrayList<>();

    @io.swagger.v3.oas.annotations.media.Schema(example = (("{" + "\"image_url\": \"image url\",") + "\"css_url\": \"css url\"") + "}")
    private java.util.Map<java.lang.String, java.lang.Object> customBranding;

    private uk.gov.pay.adminusers.model.MerchantDetails merchantDetails;

    private boolean redirectToServiceImmediatelyOnTerminalState;

    private boolean collectBillingAddress;

    private java.lang.String defaultBillingAddressCountry;

    private uk.gov.pay.adminusers.model.GoLiveStage goLiveStage;

    @io.swagger.v3.oas.annotations.media.Schema(example = "false")
    private boolean experimentalFeaturesEnabled;

    @io.swagger.v3.oas.annotations.media.Schema(example = "local government")
    private java.lang.String sector;

    @io.swagger.v3.oas.annotations.media.Schema(example = "false")
    private boolean internal;

    @io.swagger.v3.oas.annotations.media.Schema(example = "false")
    private boolean archived;

    @io.swagger.v3.oas.annotations.media.Schema(example = "false")
    private boolean agentInitiatedMotoEnabled;

    private uk.gov.pay.adminusers.model.PspTestAccountStage currentPspTestAccountStage;

    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = uk.gov.service.payments.commons.api.json.ApiResponseDateTimeSerializer.class)
    @io.swagger.v3.oas.annotations.media.Schema(example = "2022-04-01T12:07:46.568Z")
    private java.time.ZonedDateTime createdDate;

    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = uk.gov.service.payments.commons.api.json.ApiResponseDateTimeSerializer.class)
    @io.swagger.v3.oas.annotations.media.Schema(example = "2022-04-09T18:07:46.568Z")
    private java.time.ZonedDateTime wentLiveDate;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private uk.gov.pay.adminusers.model.ServiceName serviceName;

    public static uk.gov.pay.adminusers.model.Service from() {
        return uk.gov.pay.adminusers.model.Service.from(new uk.gov.pay.adminusers.model.ServiceName(uk.gov.pay.adminusers.model.Service.DEFAULT_NAME_VALUE));
    }

    public static uk.gov.pay.adminusers.model.Service from(uk.gov.pay.adminusers.model.ServiceName serviceName) {
        return uk.gov.pay.adminusers.model.Service.from(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt(), uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid(), serviceName);
    }

    public static uk.gov.pay.adminusers.model.Service from(java.lang.Integer id, java.lang.String externalId, uk.gov.pay.adminusers.model.ServiceName serviceName) {
        return uk.gov.pay.adminusers.model.Service.from(id, externalId, serviceName, false, true, uk.gov.pay.adminusers.persistence.entity.ServiceEntity.DEFAULT_BILLING_ADDRESS_COUNTRY, uk.gov.pay.adminusers.model.GoLiveStage.NOT_STARTED, false, false, null, false, false, null, null, null);
    }

    public static uk.gov.pay.adminusers.model.Service from(java.lang.Integer id, java.lang.String externalId, uk.gov.pay.adminusers.model.ServiceName serviceName, boolean redirectToServiceImmediatelyOnTerminalState, boolean collectBillingAddress, java.lang.String defaultBillingAddressCountry, uk.gov.pay.adminusers.model.GoLiveStage goLiveStage, boolean experimentalFeaturesEnabled, boolean agentInitiatedMotoEnabled, java.lang.String sector, boolean internal, boolean archived, java.time.ZonedDateTime createdDate, java.time.ZonedDateTime wentLiveDate, uk.gov.pay.adminusers.model.PspTestAccountStage pspTestAccountStage) {
        return new uk.gov.pay.adminusers.model.Service(id, externalId, serviceName, redirectToServiceImmediatelyOnTerminalState, collectBillingAddress, defaultBillingAddressCountry, goLiveStage, experimentalFeaturesEnabled, agentInitiatedMotoEnabled, sector, internal, archived, createdDate, wentLiveDate, pspTestAccountStage);
    }

    private Service(@com.fasterxml.jackson.annotation.JsonProperty("id")
    java.lang.Integer id, @com.fasterxml.jackson.annotation.JsonProperty("external_id")
    java.lang.String externalId, uk.gov.pay.adminusers.model.ServiceName serviceName, boolean redirectToServiceImmediatelyOnTerminalState, boolean collectBillingAddress, java.lang.String defaultBillingAddressCountry, uk.gov.pay.adminusers.model.GoLiveStage goLiveStage, boolean experimentalFeaturesEnabled, boolean agentInitiatedMotoEnabled, java.lang.String sector, boolean internal, boolean archived, java.time.ZonedDateTime createdDate, java.time.ZonedDateTime wentLiveDate, uk.gov.pay.adminusers.model.PspTestAccountStage currentPspTestAccountStage) {
        this.id = id;
        this.externalId = externalId;
        this.redirectToServiceImmediatelyOnTerminalState = redirectToServiceImmediatelyOnTerminalState;
        this.collectBillingAddress = collectBillingAddress;
        this.defaultBillingAddressCountry = defaultBillingAddressCountry;
        this.serviceName = serviceName;
        this.goLiveStage = goLiveStage;
        this.experimentalFeaturesEnabled = experimentalFeaturesEnabled;
        this.agentInitiatedMotoEnabled = agentInitiatedMotoEnabled;
        this.sector = sector;
        this.internal = internal;
        this.archived = archived;
        this.createdDate = createdDate;
        this.wentLiveDate = wentLiveDate;
        this.currentPspTestAccountStage = currentPspTestAccountStage;
    }

    public java.lang.String getExternalId() {
        return externalId;
    }

    public void setExternalId(java.lang.String externalId) {
        this.externalId = externalId;
    }

    public java.lang.Integer getId() {
        return id;
    }

    public void setId(java.lang.Integer id) {
        this.id = id;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("name")
    @io.swagger.v3.oas.annotations.media.Schema(example = "Some service name")
    public java.lang.String getName() {
        return serviceName.getEnglish();
    }

    @com.fasterxml.jackson.annotation.JsonProperty("_links")
    public java.util.List<uk.gov.pay.adminusers.model.Link> getLinks() {
        return links;
    }

    public void setLinks(java.util.List<uk.gov.pay.adminusers.model.Link> links) {
        this.links = links;
    }

    public java.util.List<java.lang.String> getGatewayAccountIds() {
        return gatewayAccountIds;
    }

    public void setGatewayAccountIds(java.util.List<java.lang.String> gatewayAccountIds) {
        this.gatewayAccountIds = gatewayAccountIds;
    }

    public java.util.Map<java.lang.String, java.lang.Object> getCustomBranding() {
        return customBranding;
    }

    /**
     * nullify if map is empty, so that it will be undefined in response
     *
     * @param customBranding
     */
    public void setCustomBranding(java.util.Map<java.lang.String, java.lang.Object> customBranding) {
        if ((customBranding != null) && customBranding.isEmpty()) {
            this.customBranding = null;
        } else {
            this.customBranding = customBranding;
        }
    }

    public uk.gov.pay.adminusers.model.MerchantDetails getMerchantDetails() {
        return merchantDetails;
    }

    public void setMerchantDetails(uk.gov.pay.adminusers.model.MerchantDetails merchantDetails) {
        this.merchantDetails = merchantDetails;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("service_name")
    @io.swagger.v3.oas.annotations.media.Schema(example = ((" {" + "  \"en\": \"Some service name\",") + "  \"cy\": \"Service name in welsh\"") + "    }")
    public java.util.Map<java.lang.String, java.lang.String> getServiceNames() {
        return serviceName.getEnglishAndTranslations().entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(languageToName -> languageToName.getKey().toString(), java.util.Map.Entry::getValue));
    }

    @com.fasterxml.jackson.annotation.JsonProperty("redirect_to_service_immediately_on_terminal_state")
    @io.swagger.v3.oas.annotations.media.Schema(example = "false")
    public boolean isRedirectToServiceImmediatelyOnTerminalState() {
        return redirectToServiceImmediatelyOnTerminalState;
    }

    public void setRedirectToServiceImmediatelyOnTerminalState(boolean redirectToServiceImmediatelyOnTerminalState) {
        this.redirectToServiceImmediatelyOnTerminalState = redirectToServiceImmediatelyOnTerminalState;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("collect_billing_address")
    @io.swagger.v3.oas.annotations.media.Schema(example = "true")
    public boolean isCollectBillingAddress() {
        return collectBillingAddress;
    }

    public void setCollectBillingAddress(boolean collectBillingAddress) {
        this.collectBillingAddress = collectBillingAddress;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("default_billing_address_country")
    @io.swagger.v3.oas.annotations.media.Schema(example = "GB")
    public java.lang.String getDefaultBillingAddressCountry() {
        return defaultBillingAddressCountry;
    }

    public void setDefaultBillingAddressCountry(java.lang.String defaultBillingAddressCountry) {
        this.defaultBillingAddressCountry = defaultBillingAddressCountry;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("current_go_live_stage")
    @io.swagger.v3.oas.annotations.media.Schema(example = "NOT_STARTED")
    public uk.gov.pay.adminusers.model.GoLiveStage getGoLiveStage() {
        return goLiveStage;
    }

    public void setGoLiveStage(uk.gov.pay.adminusers.model.GoLiveStage goLiveStage) {
        this.goLiveStage = goLiveStage;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("current_psp_test_account_stage")
    @io.swagger.v3.oas.annotations.media.Schema(example = "NOT_STARTED")
    public uk.gov.pay.adminusers.model.PspTestAccountStage getCurrentPspTestAccountStage() {
        return currentPspTestAccountStage;
    }

    public void setCurrentPspTestAccountStage(uk.gov.pay.adminusers.model.PspTestAccountStage currentPspTestAccountStage) {
        this.currentPspTestAccountStage = currentPspTestAccountStage;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("experimental_features_enabled")
    public boolean isExperimentalFeaturesEnabled() {
        return experimentalFeaturesEnabled;
    }

    public void setExperimentalFeaturesEnabled(boolean experimentalFeaturesEnabled) {
        this.experimentalFeaturesEnabled = experimentalFeaturesEnabled;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("agent_initiated_moto_enabled")
    public boolean isAgentInitiatedMotoEnabled() {
        return agentInitiatedMotoEnabled;
    }

    public void setAgentInitiatedMotoEnabled(boolean agentInitiatedMotoEnabled) {
        this.agentInitiatedMotoEnabled = agentInitiatedMotoEnabled;
    }

    public java.lang.String getSector() {
        return sector;
    }

    public boolean isInternal() {
        return internal;
    }

    public boolean isArchived() {
        return archived;
    }

    public java.time.ZonedDateTime getCreatedDate() {
        return createdDate;
    }

    public java.time.ZonedDateTime getWentLiveDate() {
        return wentLiveDate;
    }
}
