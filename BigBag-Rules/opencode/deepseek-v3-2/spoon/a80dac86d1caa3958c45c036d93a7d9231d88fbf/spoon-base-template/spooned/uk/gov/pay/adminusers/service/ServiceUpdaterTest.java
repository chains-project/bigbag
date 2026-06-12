package uk.gov.pay.adminusers.service;
public class ServiceUpdaterTest {
    private static final java.lang.String NON_EXISTENT_SERVICE_EXTERNAL_ID = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";

    public static final java.lang.String SERVICE_ID = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();

    private uk.gov.pay.adminusers.persistence.dao.ServiceDao serviceDao = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.dao.ServiceDao.class);

    private uk.gov.pay.adminusers.service.ServiceUpdater updater;

    private final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @org.junit.jupiter.api.BeforeEach
    public void before() {
        updater = new uk.gov.pay.adminusers.service.ServiceUpdater(serviceDao);
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_updateMerchantDetails() throws uk.gov.pay.adminusers.exception.ServiceNotFoundException {
        java.lang.String name = "name";
        java.lang.String telephoneNumber = "03069990000";
        java.lang.String addressLine1 = "something";
        java.lang.String addressLine2 = "something";
        java.lang.String addressCity = "something";
        java.lang.String addressPostcode = "something";
        java.lang.String addressCountry = "something";
        java.lang.String email = "dd-merchant@example.com";
        java.lang.String url = "https://merchant.example.com";
        uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity toUpdate = new uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity(name, telephoneNumber, addressLine1, addressLine2, addressCity, addressPostcode, addressCountry, email, url);
        uk.gov.pay.adminusers.model.UpdateMerchantDetailsRequest request = new uk.gov.pay.adminusers.model.UpdateMerchantDetailsRequest(name, telephoneNumber, addressLine1, addressLine2, addressCity, addressPostcode, addressCountry, email, url);
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.ServiceEntity.class);
        org.mockito.Mockito.when(serviceDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID)).thenReturn(java.util.Optional.of(serviceEntity));
        org.mockito.Mockito.when(serviceEntity.toService()).thenReturn(uk.gov.pay.adminusers.model.Service.from());
        uk.gov.pay.adminusers.model.Service service = updater.doUpdateMerchantDetails(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID, request);
        org.junit.jupiter.api.Assertions.assertNotNull(service);
        org.mockito.Mockito.verify(serviceEntity).setMerchantDetailsEntity(toUpdate);
        org.mockito.Mockito.verify(serviceDao).merge(serviceEntity);
    }

    @org.junit.jupiter.api.Test
    public void shouldError_updateMerchantDetails_whenServiceNotFound() throws uk.gov.pay.adminusers.exception.ServiceNotFoundException {
        java.lang.String name = "name";
        java.lang.String telephoneNumber = "03069990000";
        java.lang.String addressLine1 = "something";
        java.lang.String addressLine2 = "something";
        java.lang.String addressCity = "something";
        java.lang.String addressPostcode = "something";
        java.lang.String addressCountry = "something";
        java.lang.String email = "merchant@example.com";
        java.lang.String url = "https://merchant.example.com";
        uk.gov.pay.adminusers.model.UpdateMerchantDetailsRequest request = new uk.gov.pay.adminusers.model.UpdateMerchantDetailsRequest(name, telephoneNumber, addressLine1, addressLine2, addressCity, addressPostcode, addressCountry, email, url);
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.ServiceEntity.class);
        org.mockito.Mockito.when(serviceDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceUpdaterTest.NON_EXISTENT_SERVICE_EXTERNAL_ID)).thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.when(serviceEntity.toService()).thenReturn(uk.gov.pay.adminusers.model.Service.from());
        org.junit.jupiter.api.Assertions.assertThrows(uk.gov.pay.adminusers.exception.ServiceNotFoundException.class, () -> updater.doUpdateMerchantDetails(uk.gov.pay.adminusers.service.ServiceUpdaterTest.NON_EXISTENT_SERVICE_EXTERNAL_ID, request));
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_updateCustomBranding_whenBrandingProvided() {
        uk.gov.pay.adminusers.model.ServiceUpdateRequest request = org.mockito.Mockito.mock(uk.gov.pay.adminusers.model.ServiceUpdateRequest.class);
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.ServiceEntity.class);
        java.util.Map<java.lang.String, java.lang.Object> customBranding = java.util.Map.of("image_url", "image url", "css_url", "css url");
        org.mockito.Mockito.when(request.getPath()).thenReturn("custom_branding");
        org.mockito.Mockito.when(request.valueAsObject()).thenReturn(customBranding);
        org.mockito.Mockito.when(serviceDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID)).thenReturn(java.util.Optional.of(serviceEntity));
        org.mockito.Mockito.when(serviceEntity.toService()).thenReturn(uk.gov.pay.adminusers.model.Service.from());
        java.util.Optional<uk.gov.pay.adminusers.model.Service> maybeService = updater.doUpdate(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID, request);
        org.hamcrest.MatcherAssert.assertThat(maybeService.isPresent(), org.hamcrest.core.Is.is(true));
        org.mockito.Mockito.verify(serviceEntity).setCustomBranding(customBranding);
        org.mockito.Mockito.verify(serviceDao).merge(serviceEntity);
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_updateCustomBranding_whenBrandingNotProvided() {
        uk.gov.pay.adminusers.model.ServiceUpdateRequest request = org.mockito.Mockito.mock(uk.gov.pay.adminusers.model.ServiceUpdateRequest.class);
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.ServiceEntity.class);
        org.mockito.Mockito.when(request.getPath()).thenReturn("custom_branding");
        org.mockito.Mockito.when(request.valueAsObject()).thenReturn(null);
        org.mockito.Mockito.when(serviceDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID)).thenReturn(java.util.Optional.of(serviceEntity));
        org.mockito.Mockito.when(serviceEntity.toService()).thenReturn(uk.gov.pay.adminusers.model.Service.from());
        java.util.Optional<uk.gov.pay.adminusers.model.Service> maybeService = updater.doUpdate(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID, request);
        org.hamcrest.MatcherAssert.assertThat(maybeService.isPresent(), org.hamcrest.core.Is.is(true));
        org.mockito.Mockito.verify(serviceEntity).setCustomBranding(null);
        org.mockito.Mockito.verify(serviceDao).merge(serviceEntity);
    }

    @org.junit.jupiter.api.Test
    public void shouldSuccess_whenAddGatewayAccountToService() {
        uk.gov.pay.adminusers.model.ServiceUpdateRequest request = org.mockito.Mockito.mock(uk.gov.pay.adminusers.model.ServiceUpdateRequest.class);
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.ServiceEntity.class);
        java.util.List<java.lang.String> gatewayAccountIdsToUpdate = java.util.Arrays.asList("1", "2");
        org.mockito.Mockito.when(request.getPath()).thenReturn("gateway_account_ids");
        org.mockito.Mockito.when(request.valueAsList()).thenReturn(gatewayAccountIdsToUpdate);
        org.mockito.Mockito.when(serviceDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID)).thenReturn(java.util.Optional.of(serviceEntity));
        org.mockito.Mockito.when(serviceDao.checkIfGatewayAccountsUsed(gatewayAccountIdsToUpdate)).thenReturn(false);
        org.mockito.Mockito.when(serviceEntity.toService()).thenReturn(uk.gov.pay.adminusers.model.Service.from());
        java.util.Optional<uk.gov.pay.adminusers.model.Service> maybeService = updater.doUpdate(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID, request);
        org.hamcrest.MatcherAssert.assertThat(maybeService.isPresent(), org.hamcrest.core.Is.is(true));
        org.mockito.Mockito.verify(serviceEntity).addGatewayAccountIds(gatewayAccountIdsToUpdate.toArray(new java.lang.String[0]));
        org.mockito.Mockito.verify(serviceDao).merge(serviceEntity);
    }

    @org.junit.jupiter.api.Test
    public void shouldError_IfAGatewayAccountAlreadyAssignedToAService() {
        uk.gov.pay.adminusers.model.ServiceUpdateRequest request = org.mockito.Mockito.mock(uk.gov.pay.adminusers.model.ServiceUpdateRequest.class);
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.ServiceEntity.class);
        java.util.List<java.lang.String> gatewayAccountIdsToUpdate = java.util.Arrays.asList("1", "2");
        org.mockito.Mockito.when(request.getPath()).thenReturn("gateway_account_ids");
        org.mockito.Mockito.when(request.valueAsList()).thenReturn(gatewayAccountIdsToUpdate);
        org.mockito.Mockito.when(serviceDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID)).thenReturn(java.util.Optional.of(serviceEntity));
        org.mockito.Mockito.when(serviceDao.checkIfGatewayAccountsUsed(gatewayAccountIdsToUpdate)).thenReturn(true);
        org.junit.jupiter.api.Assertions.assertThrows(javax.ws.rs.WebApplicationException.class, () -> updater.doUpdate(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID, request));
        org.mockito.Mockito.verify(serviceEntity, org.mockito.Mockito.times(0)).addGatewayAccountIds(gatewayAccountIdsToUpdate.toArray(new java.lang.String[0]));
        org.mockito.Mockito.verify(serviceDao, org.mockito.Mockito.times(0)).merge(serviceEntity);
    }

    @org.junit.jupiter.api.Test
    public void shouldUpdateServiceNameSuccessfully() {
        java.lang.String nameToUpdate = "new-cy-name";
        uk.gov.pay.adminusers.model.ServiceUpdateRequest request = serviceUpdateRequest("replace", "service_name/cy", nameToUpdate);
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.ServiceEntity.class);
        org.mockito.Mockito.when(serviceDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID)).thenReturn(java.util.Optional.of(serviceEntity));
        org.mockito.Mockito.when(serviceEntity.toService()).thenReturn(uk.gov.pay.adminusers.model.Service.from());
        java.util.Optional<uk.gov.pay.adminusers.model.Service> maybeService = updater.doUpdate(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID, request);
        org.hamcrest.MatcherAssert.assertThat(maybeService.isPresent(), org.hamcrest.core.Is.is(true));
        uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity serviceNameEntity = uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity.from(uk.gov.service.payments.commons.model.SupportedLanguage.WELSH, nameToUpdate);
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(org.mockito.Mockito.ignoreStubs(serviceDao, serviceEntity));
        inOrder.verify(serviceEntity).addOrUpdateServiceName(serviceNameEntity);
        inOrder.verify(serviceDao).merge(serviceEntity);
    }

    @org.junit.jupiter.api.Test
    public void shouldUpdateRedirectImmediatelySuccessfully() {
        uk.gov.pay.adminusers.model.ServiceUpdateRequest request = serviceUpdateRequest("replace", "redirect_to_service_immediately_on_terminal_state", true);
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.ServiceEntity.class);
        org.mockito.Mockito.when(serviceDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID)).thenReturn(java.util.Optional.of(serviceEntity));
        org.mockito.Mockito.when(serviceEntity.toService()).thenReturn(uk.gov.pay.adminusers.model.Service.from());
        java.util.Optional<uk.gov.pay.adminusers.model.Service> maybeService = updater.doUpdate(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID, request);
        org.hamcrest.MatcherAssert.assertThat(maybeService.isPresent(), org.hamcrest.core.Is.is(true));
        org.mockito.Mockito.verify(serviceEntity).setRedirectToServiceImmediatelyOnTerminalState(true);
        org.mockito.Mockito.verify(serviceDao).merge(serviceEntity);
    }

    @org.junit.jupiter.api.Test
    public void shouldUpdateCollectBillingAddressSuccessfully() {
        uk.gov.pay.adminusers.model.ServiceUpdateRequest request = serviceUpdateRequest("replace", "collect_billing_address", false);
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.ServiceEntity.class);
        org.mockito.Mockito.when(serviceDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID)).thenReturn(java.util.Optional.of(serviceEntity));
        org.mockito.Mockito.when(serviceEntity.toService()).thenReturn(uk.gov.pay.adminusers.model.Service.from());
        java.util.Optional<uk.gov.pay.adminusers.model.Service> maybeService = updater.doUpdate(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID, request);
        org.hamcrest.MatcherAssert.assertThat(maybeService.isPresent(), org.hamcrest.core.Is.is(true));
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(serviceEntity, serviceDao);
        inOrder.verify(serviceEntity).setCollectBillingAddress(false);
        inOrder.verify(serviceDao).merge(serviceEntity);
    }

    @org.junit.jupiter.api.Test
    public void shouldUpdateCurrentGoLiveStageSuccessfully() {
        uk.gov.pay.adminusers.model.ServiceUpdateRequest request = serviceUpdateRequest("replace", "current_go_live_stage", java.lang.String.valueOf(uk.gov.pay.adminusers.model.GoLiveStage.CHOSEN_PSP_STRIPE));
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.ServiceEntity.class);
        org.mockito.Mockito.when(serviceDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID)).thenReturn(java.util.Optional.of(serviceEntity));
        org.mockito.Mockito.when(serviceEntity.toService()).thenReturn(uk.gov.pay.adminusers.model.Service.from());
        java.util.Optional<uk.gov.pay.adminusers.model.Service> maybeService = updater.doUpdate(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID, request);
        org.hamcrest.MatcherAssert.assertThat(maybeService.isPresent(), org.hamcrest.core.Is.is(true));
        org.mockito.Mockito.verify(serviceEntity).setCurrentGoLiveStage(uk.gov.pay.adminusers.model.GoLiveStage.CHOSEN_PSP_STRIPE);
        org.mockito.Mockito.verify(serviceDao).merge(serviceEntity);
    }

    @org.junit.jupiter.api.Test
    public void shouldUpdateCurrentPspTestAccountStageSuccessfully() {
        uk.gov.pay.adminusers.model.ServiceUpdateRequest request = serviceUpdateRequest("replace", "current_psp_test_account_stage", java.lang.String.valueOf(uk.gov.pay.adminusers.model.PspTestAccountStage.REQUEST_SUBMITTED));
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.ServiceEntity.class);
        org.mockito.Mockito.when(serviceDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID)).thenReturn(java.util.Optional.of(serviceEntity));
        org.mockito.Mockito.when(serviceEntity.toService()).thenReturn(uk.gov.pay.adminusers.model.Service.from());
        java.util.Optional<uk.gov.pay.adminusers.model.Service> maybeService = updater.doUpdate(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID, request);
        org.hamcrest.MatcherAssert.assertThat(maybeService.isPresent(), org.hamcrest.core.Is.is(true));
        org.mockito.Mockito.verify(serviceEntity).setCurrentPspTestAccountStage(uk.gov.pay.adminusers.model.PspTestAccountStage.REQUEST_SUBMITTED);
        org.mockito.Mockito.verify(serviceDao).merge(serviceEntity);
    }

    @org.junit.jupiter.api.Test
    public void shouldUpdateSectorSuccessfully() {
        uk.gov.pay.adminusers.model.ServiceUpdateRequest request = serviceUpdateRequest("replace", "sector", "local government");
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.ServiceEntity.class);
        org.mockito.Mockito.when(serviceDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID)).thenReturn(java.util.Optional.of(serviceEntity));
        org.mockito.Mockito.when(serviceEntity.toService()).thenReturn(uk.gov.pay.adminusers.model.Service.from());
        java.util.Optional<uk.gov.pay.adminusers.model.Service> maybeService = updater.doUpdate(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID, request);
        org.hamcrest.MatcherAssert.assertThat(maybeService.isPresent(), org.hamcrest.core.Is.is(true));
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(serviceEntity, serviceDao);
        inOrder.verify(serviceEntity).setSector("local government");
        inOrder.verify(serviceDao).merge(serviceEntity);
    }

    @org.junit.jupiter.api.Test
    public void shouldUpdateInternalSuccessfully() {
        uk.gov.pay.adminusers.model.ServiceUpdateRequest request = serviceUpdateRequest("replace", "internal", true);
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.ServiceEntity.class);
        org.mockito.Mockito.when(serviceDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID)).thenReturn(java.util.Optional.of(serviceEntity));
        org.mockito.Mockito.when(serviceEntity.toService()).thenReturn(uk.gov.pay.adminusers.model.Service.from());
        java.util.Optional<uk.gov.pay.adminusers.model.Service> maybeService = updater.doUpdate(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID, request);
        org.hamcrest.MatcherAssert.assertThat(maybeService.isPresent(), org.hamcrest.core.Is.is(true));
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(serviceEntity, serviceDao);
        inOrder.verify(serviceEntity).setInternal(true);
        inOrder.verify(serviceDao).merge(serviceEntity);
    }

    @org.junit.jupiter.api.Test
    public void shouldUpdateArchivedSuccessfully() {
        uk.gov.pay.adminusers.model.ServiceUpdateRequest request = serviceUpdateRequest("replace", "archived", true);
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.ServiceEntity.class);
        org.mockito.Mockito.when(serviceDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID)).thenReturn(java.util.Optional.of(serviceEntity));
        org.mockito.Mockito.when(serviceEntity.toService()).thenReturn(uk.gov.pay.adminusers.model.Service.from());
        java.util.Optional<uk.gov.pay.adminusers.model.Service> maybeService = updater.doUpdate(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID, request);
        org.hamcrest.MatcherAssert.assertThat(maybeService.isPresent(), org.hamcrest.core.Is.is(true));
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(serviceEntity, serviceDao);
        inOrder.verify(serviceEntity).setArchived(true);
        inOrder.verify(serviceDao).merge(serviceEntity);
    }

    @org.junit.jupiter.api.Test
    public void shouldUpdateAgentInitiatedMotoEnabledSuccessfully() {
        uk.gov.pay.adminusers.model.ServiceUpdateRequest request = serviceUpdateRequest("replace", "agent_initiated_moto_enabled", true);
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.ServiceEntity.class);
        org.mockito.Mockito.when(serviceDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID)).thenReturn(java.util.Optional.of(serviceEntity));
        org.mockito.Mockito.when(serviceEntity.toService()).thenReturn(uk.gov.pay.adminusers.model.Service.from());
        java.util.Optional<uk.gov.pay.adminusers.model.Service> maybeService = updater.doUpdate(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID, request);
        org.hamcrest.MatcherAssert.assertThat(maybeService.isPresent(), org.hamcrest.core.Is.is(true));
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(serviceEntity, serviceDao);
        inOrder.verify(serviceEntity).setAgentInitiatedMotoEnabled(true);
        inOrder.verify(serviceDao).merge(serviceEntity);
    }

    @org.junit.jupiter.api.Test
    public void shouldUpdateWentLiveDateSuccessfully() {
        uk.gov.pay.adminusers.model.ServiceUpdateRequest request = serviceUpdateRequest("replace", "went_live_date", "2020-02-28T01:02:03Z");
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.ServiceEntity.class);
        org.mockito.Mockito.when(serviceDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID)).thenReturn(java.util.Optional.of(serviceEntity));
        org.mockito.Mockito.when(serviceEntity.toService()).thenReturn(uk.gov.pay.adminusers.model.Service.from());
        java.util.Optional<uk.gov.pay.adminusers.model.Service> maybeService = updater.doUpdate(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID, request);
        org.hamcrest.MatcherAssert.assertThat(maybeService.isPresent(), org.hamcrest.core.Is.is(true));
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(serviceEntity, serviceDao);
        inOrder.verify(serviceEntity).setWentLiveDate(java.time.ZonedDateTime.of(2020, 2, 28, 1, 2, 3, 0, java.time.ZoneOffset.UTC));
        inOrder.verify(serviceDao).merge(serviceEntity);
    }

    @org.junit.jupiter.api.Test
    public void shouldUpdateDefaultBillingAddressCountrySuccessfully() {
        uk.gov.pay.adminusers.model.ServiceUpdateRequest request = serviceUpdateRequest("replace", "default_billing_address_country", "IE");
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.entity.ServiceEntity.class);
        org.mockito.Mockito.when(serviceDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID)).thenReturn(java.util.Optional.of(serviceEntity));
        org.mockito.Mockito.when(serviceEntity.toService()).thenReturn(uk.gov.pay.adminusers.model.Service.from());
        java.util.Optional<uk.gov.pay.adminusers.model.Service> maybeService = updater.doUpdate(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID, request);
        org.hamcrest.MatcherAssert.assertThat(maybeService.isPresent(), org.hamcrest.core.Is.is(true));
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(serviceEntity, serviceDao);
        inOrder.verify(serviceEntity).setDefaultBillingAddressCountry("IE");
        inOrder.verify(serviceDao).merge(serviceEntity);
    }

    @org.junit.jupiter.api.Test
    public void shouldUpdateMerchantDetailsNameSuccessfully_WhenNoExistingMerchantDetails() {
        java.lang.String name = "Cake service";
        uk.gov.pay.adminusers.model.ServiceUpdateRequest serviceRequest = serviceUpdateRequest("replace", "merchant_details/name", name);
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().withMerchantDetailsEntity(null).build();
        org.mockito.Mockito.when(serviceDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID)).thenReturn(java.util.Optional.of(serviceEntity));
        java.util.Optional<uk.gov.pay.adminusers.model.Service> maybeService = updater.doUpdate(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID, serviceRequest);
        org.hamcrest.MatcherAssert.assertThat(maybeService.isPresent(), org.hamcrest.core.Is.is(true));
        org.mockito.Mockito.verify(serviceDao).merge(serviceEntity);
        org.hamcrest.MatcherAssert.assertThat(maybeService.get().getMerchantDetails().getName(), org.hamcrest.core.Is.is(name));
    }

    @org.junit.jupiter.api.Test
    public void shouldUpdateMultipleMerchantDetailsSuccessfully_WhenNoExistingMerchantDetails() {
        java.lang.String name = "Cake service";
        java.lang.String addressLine1 = "1 Spider Lane";
        java.lang.String addressLine2 = "Some";
        java.lang.String addressCity = "where";
        java.lang.String addressCountry = "over";
        java.lang.String addressPostcode = "W10 5LA";
        java.lang.String email = "someone@example.com";
        java.lang.String telephoneNumber = "000";
        java.lang.String url = "https://merchant.example.com";
        java.util.List<uk.gov.pay.adminusers.model.ServiceUpdateRequest> serviceUpdateRequests = java.util.List.of(serviceUpdateRequest("replace", "merchant_details/name", name), serviceUpdateRequest("replace", "merchant_details/address_line1", addressLine1), serviceUpdateRequest("replace", "merchant_details/address_line2", addressLine2), serviceUpdateRequest("replace", "merchant_details/address_city", addressCity), serviceUpdateRequest("replace", "merchant_details/address_country", addressCountry), serviceUpdateRequest("replace", "merchant_details/address_postcode", addressPostcode), serviceUpdateRequest("replace", "merchant_details/email", email), serviceUpdateRequest("replace", "merchant_details/telephone_number", telephoneNumber), serviceUpdateRequest("replace", "merchant_details/url", url));
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().withMerchantDetailsEntity(null).build();
        org.mockito.Mockito.when(serviceDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID)).thenReturn(java.util.Optional.of(serviceEntity));
        java.util.Optional<uk.gov.pay.adminusers.model.Service> maybeService = updater.doUpdate(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID, serviceUpdateRequests);
        org.hamcrest.MatcherAssert.assertThat(maybeService.isPresent(), org.hamcrest.core.Is.is(true));
        org.mockito.Mockito.verify(serviceDao, org.mockito.Mockito.times(9)).merge(serviceEntity);
        org.hamcrest.MatcherAssert.assertThat(maybeService.get().getMerchantDetails().getName(), org.hamcrest.core.Is.is(name));
        org.hamcrest.MatcherAssert.assertThat(maybeService.get().getMerchantDetails().getAddressLine1(), org.hamcrest.core.Is.is(addressLine1));
        org.hamcrest.MatcherAssert.assertThat(maybeService.get().getMerchantDetails().getAddressLine2(), org.hamcrest.core.Is.is(addressLine2));
        org.hamcrest.MatcherAssert.assertThat(maybeService.get().getMerchantDetails().getAddressCity(), org.hamcrest.core.Is.is(addressCity));
        org.hamcrest.MatcherAssert.assertThat(maybeService.get().getMerchantDetails().getAddressCountry(), org.hamcrest.core.Is.is(addressCountry));
        org.hamcrest.MatcherAssert.assertThat(maybeService.get().getMerchantDetails().getAddressPostcode(), org.hamcrest.core.Is.is(addressPostcode));
        org.hamcrest.MatcherAssert.assertThat(maybeService.get().getMerchantDetails().getEmail(), org.hamcrest.core.Is.is(email));
        org.hamcrest.MatcherAssert.assertThat(maybeService.get().getMerchantDetails().getTelephoneNumber(), org.hamcrest.core.Is.is(telephoneNumber));
        org.hamcrest.MatcherAssert.assertThat(maybeService.get().getMerchantDetails().getUrl(), org.hamcrest.core.Is.is(url));
    }

    @org.junit.jupiter.api.Test
    public void shouldUpdateMerchantDetailsAddressLine1Successfully_WhenExistingMerchantDetails() {
        java.lang.String updatedAddressLine1 = "1 Spider Lane";
        uk.gov.pay.adminusers.model.ServiceUpdateRequest request = serviceUpdateRequest("replace", "merchant_details/address_line1", updatedAddressLine1);
        uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity merchantDetails = uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntityBuilder.aMerchantDetailsEntity().build();
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().withMerchantDetailsEntity(merchantDetails).build();
        org.mockito.Mockito.when(serviceDao.findByExternalId(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID)).thenReturn(java.util.Optional.of(serviceEntity));
        java.util.Optional<uk.gov.pay.adminusers.model.Service> maybeService = updater.doUpdate(uk.gov.pay.adminusers.service.ServiceUpdaterTest.SERVICE_ID, request);
        org.hamcrest.MatcherAssert.assertThat(maybeService.isPresent(), org.hamcrest.core.Is.is(true));
        org.mockito.Mockito.verify(serviceDao).merge(serviceEntity);
        org.hamcrest.MatcherAssert.assertThat(maybeService.get().getMerchantDetails().getName(), org.hamcrest.core.Is.is("test-name"));
        org.hamcrest.MatcherAssert.assertThat(maybeService.get().getMerchantDetails().getAddressLine1(), org.hamcrest.core.Is.is(updatedAddressLine1));
    }

    private uk.gov.pay.adminusers.model.ServiceUpdateRequest serviceUpdateRequest(java.lang.String op, java.lang.String path, java.lang.Object value) {
        java.util.Map<java.lang.String, java.lang.Object> payload = new java.util.HashMap<>();
        payload.put("path", path);
        payload.put("op", op);
        payload.put("value", value);
        return uk.gov.pay.adminusers.model.ServiceUpdateRequest.from(mapper.valueToTree(java.util.Map.of("op", op, "path", path, "value", value)));
    }
}
