package uk.gov.pay.adminusers.persistence.dao;
class ServiceDaoIT extends uk.gov.pay.adminusers.persistence.dao.DaoTestBase {
    private uk.gov.pay.adminusers.persistence.dao.ServiceDao serviceDao;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private static final java.lang.String EN_NAME = "en-test-name";

    private static final java.lang.String CY_NAME = "gwasanaeth prawf";

    @org.junit.jupiter.api.BeforeEach
    void before() {
        uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper.truncateAllData();
        serviceDao = uk.gov.pay.adminusers.persistence.dao.DaoTestBase.env.getInstance(uk.gov.pay.adminusers.persistence.dao.ServiceDao.class);
    }

    @org.junit.jupiter.api.Test
    void shouldSaveAService_withCustomisations() throws java.lang.Exception {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity insertedServiceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().withExperimentalFeaturesEnabled(true).build();
        serviceDao.persist(insertedServiceEntity);
        java.util.List<java.util.Map<java.lang.String, java.lang.Object>> savedService = uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper.findServiceByExternalId(insertedServiceEntity.getExternalId());
        org.hamcrest.MatcherAssert.assertThat(savedService.size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(savedService.get(0).get("external_id"), org.hamcrest.core.Is.is(insertedServiceEntity.getExternalId()));
        java.util.Map<java.lang.String, java.lang.Object> storedBranding = objectMapper.readValue(savedService.get(0).get("custom_branding").toString(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
        org.hamcrest.MatcherAssert.assertThat(storedBranding, org.hamcrest.core.Is.is(insertedServiceEntity.getCustomBranding()));
        org.hamcrest.MatcherAssert.assertThat(storedBranding.keySet().size(), org.hamcrest.core.Is.is(2));
        org.hamcrest.MatcherAssert.assertThat(storedBranding.keySet(), org.hamcrest.Matchers.hasItems("image_url", "css_url"));
        org.hamcrest.MatcherAssert.assertThat(storedBranding.values(), org.hamcrest.Matchers.hasItems("image url", "css url"));
        org.hamcrest.MatcherAssert.assertThat(savedService.get(0).get("experimental_features_enabled"), org.hamcrest.core.Is.is(true));
    }

    @org.junit.jupiter.api.Test
    void shouldSaveAService_withMultipleServiceNames() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity insertedServiceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().withCustomBranding(null).withServiceNameEntity(uk.gov.service.payments.commons.model.SupportedLanguage.WELSH, uk.gov.pay.adminusers.persistence.dao.ServiceDaoIT.CY_NAME).withServiceNameEntity(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, uk.gov.pay.adminusers.persistence.dao.ServiceDaoIT.EN_NAME).build();
        serviceDao.persist(insertedServiceEntity);
        java.util.List<java.util.Map<java.lang.String, java.lang.Object>> savedService = uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper.findServiceByExternalId(insertedServiceEntity.getExternalId());
        org.hamcrest.MatcherAssert.assertThat(savedService.size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(savedService.get(0).get("external_id"), org.hamcrest.core.Is.is(insertedServiceEntity.getExternalId()));
        java.util.List<java.util.Map<java.lang.String, java.lang.Object>> savedServiceName = uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper.findServiceNameByServiceId(insertedServiceEntity.getId());
        org.hamcrest.MatcherAssert.assertThat(savedServiceName.size(), org.hamcrest.core.Is.is(2));
        savedServiceName.sort(java.util.Comparator.comparing(item -> java.lang.String.valueOf(item.get("language"))));
        org.hamcrest.MatcherAssert.assertThat(savedServiceName.get(0).get("service_id"), org.hamcrest.core.Is.is(java.lang.Long.valueOf(insertedServiceEntity.getId())));
        org.hamcrest.MatcherAssert.assertThat(savedServiceName.get(0).get("language"), org.hamcrest.core.Is.is("cy"));
        org.hamcrest.MatcherAssert.assertThat(savedServiceName.get(0).get("name"), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.persistence.dao.ServiceDaoIT.CY_NAME));
        org.hamcrest.MatcherAssert.assertThat(savedServiceName.get(1).get("service_id"), org.hamcrest.core.Is.is(java.lang.Long.valueOf(insertedServiceEntity.getId())));
        org.hamcrest.MatcherAssert.assertThat(savedServiceName.get(1).get("language"), org.hamcrest.core.Is.is("en"));
        org.hamcrest.MatcherAssert.assertThat(savedServiceName.get(1).get("name"), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.persistence.dao.ServiceDaoIT.EN_NAME));
    }

    @org.junit.jupiter.api.Test
    void shouldSaveAService_withoutCustomisations_andServiceName() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity insertedServiceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().withCustomBranding(null).withServiceNameEntity(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, uk.gov.pay.adminusers.persistence.dao.ServiceDaoIT.EN_NAME).withServiceNameEntity(uk.gov.service.payments.commons.model.SupportedLanguage.WELSH, uk.gov.pay.adminusers.persistence.dao.ServiceDaoIT.CY_NAME).build();
        serviceDao.persist(insertedServiceEntity);
        java.util.List<java.util.Map<java.lang.String, java.lang.Object>> savedService = uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper.findServiceByExternalId(insertedServiceEntity.getExternalId());
        org.hamcrest.MatcherAssert.assertThat(savedService.size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(savedService.get(0).get("external_id"), org.hamcrest.core.Is.is(insertedServiceEntity.getExternalId()));
        java.util.Map<java.lang.String, java.lang.Object> storedBranding = new uk.gov.pay.adminusers.persistence.entity.CustomBrandingConverter().convertToEntityAttribute(((org.postgresql.util.PGobject) (savedService.get(0).get("custom_branding"))));
        org.junit.jupiter.api.Assertions.assertNull(storedBranding);
        java.util.List<java.util.Map<java.lang.String, java.lang.Object>> savedServiceName = uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper.findServiceNameByServiceId(insertedServiceEntity.getId());
        savedServiceName.sort(java.util.Comparator.comparing(item -> java.lang.String.valueOf(item.get("language"))));
        org.hamcrest.MatcherAssert.assertThat(savedServiceName.size(), org.hamcrest.core.Is.is(2));
        org.hamcrest.MatcherAssert.assertThat(savedServiceName.get(0).get("service_id"), org.hamcrest.core.Is.is(java.lang.Long.valueOf(insertedServiceEntity.getId())));
        org.hamcrest.MatcherAssert.assertThat(savedServiceName.get(0).get("language"), org.hamcrest.core.Is.is("cy"));
        org.hamcrest.MatcherAssert.assertThat(savedServiceName.get(0).get("name"), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.persistence.dao.ServiceDaoIT.CY_NAME));
        org.hamcrest.MatcherAssert.assertThat(savedServiceName.get(1).get("service_id"), org.hamcrest.core.Is.is(java.lang.Long.valueOf(insertedServiceEntity.getId())));
        org.hamcrest.MatcherAssert.assertThat(savedServiceName.get(1).get("language"), org.hamcrest.core.Is.is("en"));
        org.hamcrest.MatcherAssert.assertThat(savedServiceName.get(1).get("name"), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.persistence.dao.ServiceDaoIT.EN_NAME));
    }

    @org.junit.jupiter.api.Test
    void shouldSaveAService_withMerchantDetails() {
        uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity merchantDetails = uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntityBuilder.aMerchantDetailsEntity().build();
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity insertedServiceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().withMerchantDetailsEntity(merchantDetails).build();
        serviceDao.persist(insertedServiceEntity);
        java.util.List<java.util.Map<java.lang.String, java.lang.Object>> savedService = uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper.findServiceByExternalId(insertedServiceEntity.getExternalId());
        org.hamcrest.MatcherAssert.assertThat(savedService.size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(savedService.get(0).get("external_id"), org.hamcrest.core.Is.is(insertedServiceEntity.getExternalId()));
        org.hamcrest.MatcherAssert.assertThat(savedService.get(0).get("merchant_name"), org.hamcrest.core.Is.is(merchantDetails.getName()));
        org.hamcrest.MatcherAssert.assertThat(savedService.get(0).get("merchant_telephone_number"), org.hamcrest.core.Is.is(merchantDetails.getTelephoneNumber()));
        org.hamcrest.MatcherAssert.assertThat(savedService.get(0).get("merchant_address_line1"), org.hamcrest.core.Is.is(merchantDetails.getAddressLine1()));
        org.hamcrest.MatcherAssert.assertThat(savedService.get(0).get("merchant_address_line2"), org.hamcrest.core.Is.is(merchantDetails.getAddressLine2()));
        org.hamcrest.MatcherAssert.assertThat(savedService.get(0).get("merchant_address_city"), org.hamcrest.core.Is.is(merchantDetails.getAddressCity()));
        org.hamcrest.MatcherAssert.assertThat(savedService.get(0).get("merchant_address_postcode"), org.hamcrest.core.Is.is(merchantDetails.getAddressPostcode()));
        org.hamcrest.MatcherAssert.assertThat(savedService.get(0).get("merchant_address_country"), org.hamcrest.core.Is.is(merchantDetails.getAddressCountryCode()));
        org.hamcrest.MatcherAssert.assertThat(savedService.get(0).get("merchant_email"), org.hamcrest.core.Is.is(merchantDetails.getEmail()));
        org.hamcrest.MatcherAssert.assertThat(savedService.get(0).get("merchant_url"), org.hamcrest.core.Is.is(merchantDetails.getUrl()));
    }

    @org.junit.jupiter.api.Test
    void shouldFindByServiceExternalId() {
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC);
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity insertedServiceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().withExperimentalFeaturesEnabled(true).build();
        uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper.insertServiceEntity(insertedServiceEntity);
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.ServiceEntity> maybeServiceEntity = serviceDao.findByExternalId(insertedServiceEntity.getExternalId());
        org.junit.jupiter.api.Assertions.assertTrue(maybeServiceEntity.isPresent());
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity foundServiceEntity = maybeServiceEntity.get();
        org.hamcrest.MatcherAssert.assertThat(foundServiceEntity.isExperimentalFeaturesEnabled(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(foundServiceEntity.getCreatedDate(), org.hamcrest.core.Is.is(insertedServiceEntity.getCreatedDate()));
        assertServiceEntity(insertedServiceEntity, foundServiceEntity);
        assertMerchantDetails(foundServiceEntity.getMerchantDetailsEntity(), insertedServiceEntity.getMerchantDetailsEntity());
        assertCustomBranding(foundServiceEntity);
    }

    @org.junit.jupiter.api.Test
    void shouldFindByENServiceName() {
        var se1 = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().withCustomBranding(null).withServiceNameEntity(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, "register a birth").build();
        var se2 = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().withCustomBranding(null).withServiceNameEntity(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, "bulky waste collection").build();
        uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper.insertServiceEntity(se1);
        uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper.insertServiceEntity(se2);
        java.util.List<uk.gov.pay.adminusers.persistence.entity.ServiceEntity> shouldHaveServiceEntities = serviceDao.findByENServiceName("birth");
        org.hamcrest.MatcherAssert.assertThat(shouldHaveServiceEntities, org.hamcrest.core.Is.is(org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())));
        org.hamcrest.MatcherAssert.assertThat(shouldHaveServiceEntities.size(), org.hamcrest.core.Is.is(1));
        var foundServiceNames = shouldHaveServiceEntities.stream().map(uk.gov.pay.adminusers.persistence.entity.ServiceEntity::toService).map(uk.gov.pay.adminusers.model.Service::getName).collect(java.util.stream.Collectors.toUnmodifiableList());
        org.hamcrest.MatcherAssert.assertThat(foundServiceNames, org.hamcrest.Matchers.contains("register a birth"));
        java.util.List<uk.gov.pay.adminusers.persistence.entity.ServiceEntity> shouldNotHaveServiceEntities = serviceDao.findByENServiceName("random");
        org.hamcrest.MatcherAssert.assertThat(shouldNotHaveServiceEntities, org.hamcrest.core.Is.is(org.hamcrest.Matchers.empty()));
    }

    @org.junit.jupiter.api.Test
    void shouldFindByServiceMerchantName() {
        var merchantDetails1 = uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntityBuilder.aMerchantDetailsEntity().withName("Royal Borough of Gondor").build();
        var merchantDetails2 = uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntityBuilder.aMerchantDetailsEntity().withName("Royal Borough of Rivendell").build();
        var se1 = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().withCustomBranding(null).withServiceNameEntity(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, "register a birth").withMerchantDetailsEntity(merchantDetails1).build();
        var se2 = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().withCustomBranding(null).withServiceNameEntity(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, "bulky waste collection").withMerchantDetailsEntity(merchantDetails2).build();
        uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper.insertServiceEntity(se1);
        uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper.insertServiceEntity(se2);
        java.util.List<uk.gov.pay.adminusers.persistence.entity.ServiceEntity> shouldHaveServiceEntities = serviceDao.findByServiceMerchantName("royal borough");
        org.hamcrest.MatcherAssert.assertThat(shouldHaveServiceEntities, org.hamcrest.core.Is.is(org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())));
        org.hamcrest.MatcherAssert.assertThat(shouldHaveServiceEntities.size(), org.hamcrest.core.Is.is(2));
        var foundServiceNames = shouldHaveServiceEntities.stream().map(uk.gov.pay.adminusers.persistence.entity.ServiceEntity::toService).map(uk.gov.pay.adminusers.model.Service::getName).collect(java.util.stream.Collectors.toUnmodifiableList());
        org.hamcrest.MatcherAssert.assertThat(foundServiceNames, org.hamcrest.Matchers.containsInAnyOrder("register a birth", "bulky waste collection"));
        java.util.List<uk.gov.pay.adminusers.persistence.entity.ServiceEntity> shouldNotHaveServiceEntities = serviceDao.findByServiceMerchantName("of");
        org.hamcrest.MatcherAssert.assertThat(shouldNotHaveServiceEntities, org.hamcrest.core.Is.is(org.hamcrest.Matchers.empty()));
    }

    @org.junit.jupiter.api.Test
    void shouldReturnServiceValuesFromDatabase() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity insertedServiceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().withRedirectToServiceImmediatelyOnTerminalState(true).withCreatedDate(java.time.ZonedDateTime.parse("2020-11-01T00:00:00Z")).build();
        uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper.insertServiceEntity(insertedServiceEntity);
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.ServiceEntity> maybeServiceEntity = serviceDao.findByExternalId(insertedServiceEntity.getExternalId());
        org.junit.jupiter.api.Assertions.assertTrue(maybeServiceEntity.isPresent());
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity foundServiceEntity = maybeServiceEntity.get();
        assertServiceEntity(insertedServiceEntity, foundServiceEntity);
    }

    @org.junit.jupiter.api.Test
    void shouldFindServiceWithMultipleLanguage_byServiceExternalId() {
        java.util.Set<uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity> serviceNames = new java.util.HashSet<>(java.util.List.of(uk.gov.pay.adminusers.persistence.dao.ServiceDaoIT.createServiceName(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, uk.gov.pay.adminusers.persistence.dao.ServiceDaoIT.EN_NAME), uk.gov.pay.adminusers.persistence.dao.ServiceDaoIT.createServiceName(uk.gov.service.payments.commons.model.SupportedLanguage.WELSH, uk.gov.pay.adminusers.persistence.dao.ServiceDaoIT.CY_NAME)));
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity insertedServiceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().withServiceName(serviceNames).build();
        uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper.insertServiceEntity(insertedServiceEntity);
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.ServiceEntity> serviceEntity = serviceDao.findByExternalId(insertedServiceEntity.getExternalId());
        org.junit.jupiter.api.Assertions.assertTrue(serviceEntity.isPresent());
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity foundServiceEntity = serviceEntity.get();
        assertServiceEntity(insertedServiceEntity, foundServiceEntity);
        assertMerchantDetails(insertedServiceEntity.getMerchantDetailsEntity(), foundServiceEntity.getMerchantDetailsEntity());
        assertCustomBranding(foundServiceEntity);
        org.hamcrest.MatcherAssert.assertThat(foundServiceEntity.getServiceNames().size(), org.hamcrest.core.Is.is(2));
        org.hamcrest.MatcherAssert.assertThat(foundServiceEntity.getServiceNames(), org.hamcrest.Matchers.hasKey(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH));
        org.hamcrest.MatcherAssert.assertThat(foundServiceEntity.getServiceNames(), org.hamcrest.Matchers.hasKey(uk.gov.service.payments.commons.model.SupportedLanguage.WELSH));
    }

    @org.junit.jupiter.api.Test
    void shouldFindByGatewayAccountId() {
        uk.gov.pay.adminusers.persistence.entity.GatewayAccountIdEntity gatewayAccountIdEntity = new uk.gov.pay.adminusers.persistence.entity.GatewayAccountIdEntity();
        java.lang.String gatewayAccountId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        gatewayAccountIdEntity.setGatewayAccountId(gatewayAccountId);
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity insertedServiceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().withGatewayAccounts(java.util.Collections.singletonList(gatewayAccountIdEntity)).build();
        gatewayAccountIdEntity.setService(insertedServiceEntity);
        uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper.insertServiceEntity(insertedServiceEntity);
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.ServiceEntity> optionalService = serviceDao.findByGatewayAccountId(insertedServiceEntity.getGatewayAccountId().getGatewayAccountId());
        org.hamcrest.MatcherAssert.assertThat(optionalService.isPresent(), org.hamcrest.core.Is.is(true));
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity foundServiceEntity = optionalService.get();
        assertServiceEntity(insertedServiceEntity, foundServiceEntity);
    }

    @org.junit.jupiter.api.Test
    void shouldGetRoleCountForAService() {
        java.lang.String serviceExternalId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.Integer roleId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt();
        setupUsersForServiceAndRole(serviceExternalId, roleId, 3);
        java.lang.Long count = serviceDao.countOfUsersWithRoleForService(serviceExternalId, roleId);
        org.hamcrest.MatcherAssert.assertThat(count, org.hamcrest.core.Is.is(3L));
    }

    @org.junit.jupiter.api.Test
    void shouldMergeGoLiveStage() {
        uk.gov.pay.adminusers.persistence.entity.GatewayAccountIdEntity gatewayAccountIdEntity = new uk.gov.pay.adminusers.persistence.entity.GatewayAccountIdEntity();
        java.lang.String gatewayAccountId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        gatewayAccountIdEntity.setGatewayAccountId(gatewayAccountId);
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity insertedServiceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().withGatewayAccounts(java.util.Collections.singletonList(gatewayAccountIdEntity)).build();
        gatewayAccountIdEntity.setService(insertedServiceEntity);
        uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper.insertServiceEntity(insertedServiceEntity);
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.ServiceEntity> optionalService = serviceDao.findByGatewayAccountId(insertedServiceEntity.getGatewayAccountId().getGatewayAccountId());
        org.hamcrest.MatcherAssert.assertThat(optionalService.isPresent(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(optionalService.get().getCurrentGoLiveStage(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.GoLiveStage.NOT_STARTED));
        optionalService.get().setCurrentGoLiveStage(uk.gov.pay.adminusers.model.GoLiveStage.CHOSEN_PSP_STRIPE);
        serviceDao.merge(optionalService.get());
        optionalService = serviceDao.findByGatewayAccountId(insertedServiceEntity.getGatewayAccountId().getGatewayAccountId());
        org.hamcrest.MatcherAssert.assertThat(optionalService.isPresent(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(optionalService.get().getCurrentGoLiveStage(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.GoLiveStage.CHOSEN_PSP_STRIPE));
    }

    @org.junit.jupiter.api.Test
    void shouldMergePSPTestAccountStage() {
        uk.gov.pay.adminusers.persistence.entity.GatewayAccountIdEntity gatewayAccountIdEntity = new uk.gov.pay.adminusers.persistence.entity.GatewayAccountIdEntity();
        java.lang.String gatewayAccountId = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        gatewayAccountIdEntity.setGatewayAccountId(gatewayAccountId);
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity insertedServiceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().withGatewayAccounts(java.util.Collections.singletonList(gatewayAccountIdEntity)).build();
        gatewayAccountIdEntity.setService(insertedServiceEntity);
        uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper.insertServiceEntity(insertedServiceEntity);
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.ServiceEntity> optionalService = serviceDao.findByGatewayAccountId(insertedServiceEntity.getGatewayAccountId().getGatewayAccountId());
        org.hamcrest.MatcherAssert.assertThat(optionalService.isPresent(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(optionalService.get().getCurrentPspTestAccountStage(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.PspTestAccountStage.NOT_STARTED));
        optionalService.get().setCurrentPspTestAccountStage(uk.gov.pay.adminusers.model.PspTestAccountStage.REQUEST_SUBMITTED);
        serviceDao.merge(optionalService.get());
        optionalService = serviceDao.findByGatewayAccountId(insertedServiceEntity.getGatewayAccountId().getGatewayAccountId());
        org.hamcrest.MatcherAssert.assertThat(optionalService.isPresent(), org.hamcrest.core.Is.is(true));
        org.hamcrest.MatcherAssert.assertThat(optionalService.get().getCurrentPspTestAccountStage(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.PspTestAccountStage.REQUEST_SUBMITTED));
    }

    private void setupUsersForServiceAndRole(java.lang.String externalId, int roleId, int noOfUsers) {
        uk.gov.pay.adminusers.model.Permission perm1 = aPermission();
        uk.gov.pay.adminusers.model.Permission perm2 = aPermission();
        uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper.add(perm1).add(perm2);
        uk.gov.pay.adminusers.model.Role role = uk.gov.pay.adminusers.model.Role.role(roleId, "role-" + roleId, "role-desc-" + roleId);
        role.setPermissions(java.util.Set.of(perm1, perm2));
        uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper.add(role);
        java.lang.String gatewayAccountId1 = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt().toString();
        uk.gov.pay.adminusers.model.Service service1 = uk.gov.pay.adminusers.model.Service.from(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt(), externalId, new uk.gov.pay.adminusers.model.ServiceName(uk.gov.pay.adminusers.model.Service.DEFAULT_NAME_VALUE));
        uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper.addService(service1, gatewayAccountId1);
        java.util.stream.IntStream.range(0, noOfUsers - 1).forEach(i -> {
            java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
            java.lang.String email = username + "@example.com";
            uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).withServiceRole(service1, roleId).withUsername(username).withEmail(email).insertUser();
        });
        // unmatching service
        java.lang.String gatewayAccountId2 = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt().toString();
        java.lang.Integer serviceId2 = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt();
        java.lang.String externalId2 = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        uk.gov.pay.adminusers.model.Service service2 = uk.gov.pay.adminusers.model.Service.from(serviceId2, externalId2, new uk.gov.pay.adminusers.model.ServiceName(uk.gov.pay.adminusers.model.Service.DEFAULT_NAME_VALUE));
        uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper.addService(service2, gatewayAccountId2);
        // same user 2 diff services - should count only once
        java.lang.String username3 = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email3 = username3 + "@example.com";
        uk.gov.pay.adminusers.model.User user3 = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).withServiceRole(service1, roleId).withUsername(username3).withEmail(email3).insertUser();
        uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper.addUserServiceRole(user3.getId(), serviceId2, role.getId());
    }

    private static uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity createServiceName(uk.gov.service.payments.commons.model.SupportedLanguage language, java.lang.String name) {
        uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity serviceNameEntity = uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity.from(language, name);
        serviceNameEntity.setId(((long) (org.apache.commons.lang3.RandomUtils.nextInt())));
        return serviceNameEntity;
    }

    private void assertMerchantDetails(uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity thisEntity, uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity thatEntity) {
        org.hamcrest.MatcherAssert.assertThat(thisEntity.getName(), org.hamcrest.core.Is.is(thatEntity.getName()));
        org.hamcrest.MatcherAssert.assertThat(thisEntity.getTelephoneNumber(), org.hamcrest.core.Is.is(thatEntity.getTelephoneNumber()));
        org.hamcrest.MatcherAssert.assertThat(thisEntity.getAddressLine1(), org.hamcrest.core.Is.is(thatEntity.getAddressLine1()));
        org.hamcrest.MatcherAssert.assertThat(thisEntity.getAddressLine2(), org.hamcrest.core.Is.is(thatEntity.getAddressLine2()));
        org.hamcrest.MatcherAssert.assertThat(thisEntity.getAddressCity(), org.hamcrest.core.Is.is(thatEntity.getAddressCity()));
        org.hamcrest.MatcherAssert.assertThat(thisEntity.getAddressPostcode(), org.hamcrest.core.Is.is(thatEntity.getAddressPostcode()));
        org.hamcrest.MatcherAssert.assertThat(thisEntity.getAddressCountryCode(), org.hamcrest.core.Is.is(thatEntity.getAddressCountryCode()));
        org.hamcrest.MatcherAssert.assertThat(thisEntity.getEmail(), org.hamcrest.core.Is.is(thatEntity.getEmail()));
        org.hamcrest.MatcherAssert.assertThat(thisEntity.getUrl(), org.hamcrest.core.Is.is(thatEntity.getUrl()));
    }

    private void assertServiceEntity(uk.gov.pay.adminusers.persistence.entity.ServiceEntity thisEntity, uk.gov.pay.adminusers.persistence.entity.ServiceEntity thatEntity) {
        org.hamcrest.MatcherAssert.assertThat(thisEntity.getId(), org.hamcrest.core.Is.is(thatEntity.getId()));
        org.hamcrest.MatcherAssert.assertThat(thisEntity.getExternalId(), org.hamcrest.core.Is.is(thatEntity.getExternalId()));
        org.hamcrest.MatcherAssert.assertThat(thisEntity.getServiceNames().get(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH).getName(), org.hamcrest.core.Is.is(thatEntity.getServiceNames().get(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH).getName()));
        org.hamcrest.MatcherAssert.assertThat(thisEntity.isRedirectToServiceImmediatelyOnTerminalState(), org.hamcrest.core.Is.is(thatEntity.isRedirectToServiceImmediatelyOnTerminalState()));
        org.hamcrest.MatcherAssert.assertThat(thisEntity.isCollectBillingAddress(), org.hamcrest.core.Is.is(thatEntity.isCollectBillingAddress()));
        org.hamcrest.MatcherAssert.assertThat(thisEntity.getDefaultBillingAddressCountry(), org.hamcrest.core.Is.is(thatEntity.getDefaultBillingAddressCountry()));
    }

    private void assertCustomBranding(uk.gov.pay.adminusers.persistence.entity.ServiceEntity insertedServiceEntity) {
        org.hamcrest.MatcherAssert.assertThat(insertedServiceEntity.getCustomBranding().keySet().size(), org.hamcrest.core.Is.is(2));
        org.hamcrest.MatcherAssert.assertThat(insertedServiceEntity.getCustomBranding().keySet(), org.hamcrest.Matchers.hasItems("image_url", "css_url"));
        org.hamcrest.MatcherAssert.assertThat(insertedServiceEntity.getCustomBranding().values(), org.hamcrest.Matchers.hasItems("image url", "css url"));
    }
}
