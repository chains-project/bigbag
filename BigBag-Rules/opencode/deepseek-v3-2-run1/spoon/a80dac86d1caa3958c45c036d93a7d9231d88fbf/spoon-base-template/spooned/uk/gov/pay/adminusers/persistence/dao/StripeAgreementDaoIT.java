package uk.gov.pay.adminusers.persistence.dao;
public class StripeAgreementDaoIT extends uk.gov.pay.adminusers.persistence.dao.DaoTestBase {
    private uk.gov.pay.adminusers.persistence.dao.StripeAgreementDao stripeAgreementDao;

    private uk.gov.pay.adminusers.persistence.dao.ServiceDao serviceDao;

    @org.junit.jupiter.api.BeforeEach
    public void before() {
        stripeAgreementDao = uk.gov.pay.adminusers.persistence.dao.DaoTestBase.env.getInstance(uk.gov.pay.adminusers.persistence.dao.StripeAgreementDao.class);
        serviceDao = uk.gov.pay.adminusers.persistence.dao.DaoTestBase.env.getInstance(uk.gov.pay.adminusers.persistence.dao.ServiceDao.class);
    }

    @org.junit.jupiter.api.Test
    public void shouldPersistStripeAgreementEntity() {
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).withGatewayAccountIds(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt().toString()).insertService();
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.ServiceEntity> serviceEntity = serviceDao.findByExternalId(service.getExternalId());
        org.hamcrest.MatcherAssert.assertThat(serviceEntity.isPresent(), org.hamcrest.core.Is.is(true));
        uk.gov.pay.adminusers.persistence.entity.StripeAgreementEntity stripeAgreementEntity = new uk.gov.pay.adminusers.persistence.entity.StripeAgreementEntity(serviceEntity.get(), "192.0.2.0", java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")));
        stripeAgreementDao.persist(stripeAgreementEntity);
        org.hamcrest.MatcherAssert.assertThat(stripeAgreementEntity.getId(), org.hamcrest.core.Is.is(org.hamcrest.core.IsNull.notNullValue()));
        java.util.List<java.util.Map<java.lang.String, java.lang.Object>> searchResults = uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper.findStripeAgreementById(stripeAgreementEntity.getId());
        org.hamcrest.MatcherAssert.assertThat(searchResults.size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(searchResults.get(0).get("service_id"), org.hamcrest.core.Is.is(stripeAgreementEntity.getService().getId()));
        org.hamcrest.MatcherAssert.assertThat(searchResults.get(0).get("ip_address"), org.hamcrest.core.Is.is(stripeAgreementEntity.getIpAddress()));
        org.hamcrest.MatcherAssert.assertThat(searchResults.get(0).get("id"), org.hamcrest.core.Is.is(stripeAgreementEntity.getId()));
        org.hamcrest.MatcherAssert.assertThat(searchResults.get(0).get("agreement_time"), org.hamcrest.core.Is.is(java.sql.Timestamp.from(stripeAgreementEntity.getAgreementTime().toInstant())));
    }

    @org.junit.jupiter.api.Test
    public void shouldNotPersistStripeAgreementEntityWhenOneAlreadyExistsForService() {
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).withGatewayAccountIds(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt().toString()).insertService();
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.ServiceEntity> serviceEntity = serviceDao.findByExternalId(service.getExternalId());
        org.hamcrest.MatcherAssert.assertThat(serviceEntity.isPresent(), org.hamcrest.core.Is.is(true));
        uk.gov.pay.adminusers.persistence.entity.StripeAgreementEntity stripeAgreementEntity = new uk.gov.pay.adminusers.persistence.entity.StripeAgreementEntity(serviceEntity.get(), "192.0.2.0", java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")));
        stripeAgreementDao.persist(stripeAgreementEntity);
        uk.gov.pay.adminusers.persistence.entity.StripeAgreementEntity anotherStripeAgreementEntity = new uk.gov.pay.adminusers.persistence.entity.StripeAgreementEntity(serviceEntity.get(), "192.0.2.1", java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")));
        try {
            stripeAgreementDao.persist(anotherStripeAgreementEntity);
            org.junit.jupiter.api.Assertions.fail();
        } catch (javax.persistence.RollbackException e) {
            org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains(("Key (service_id)=(" + serviceEntity.get().getId()) + ") already exists."));
        }
    }

    @org.junit.jupiter.api.Test
    public void shouldFindStripeAgreementEntityByServiceId() {
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).insertService();
        java.time.ZonedDateTime agreementTime = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC);
        java.lang.String ipAddress = "192.0.2.0";
        uk.gov.pay.adminusers.fixtures.StripeAgreementDbFixture.stripeAgreementDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).withAgreementTime(agreementTime).withIpAddress(ipAddress).withServiceId(service.getId()).insert();
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.StripeAgreementEntity> maybeStripeAgreementEntity = stripeAgreementDao.findByServiceExternalId(service.getExternalId());
        org.junit.jupiter.api.Assertions.assertTrue(maybeStripeAgreementEntity.isPresent());
        org.hamcrest.MatcherAssert.assertThat(maybeStripeAgreementEntity.get().getIpAddress(), org.hamcrest.core.Is.is(ipAddress));
        org.hamcrest.MatcherAssert.assertThat(maybeStripeAgreementEntity.get().getService().getId(), org.hamcrest.core.Is.is(service.getId()));
        org.hamcrest.MatcherAssert.assertThat(maybeStripeAgreementEntity.get().getAgreementTime(), org.hamcrest.core.Is.is(agreementTime));
    }

    @org.junit.jupiter.api.Test
    public void shouldNotFindStripeAgreementEntityWhenNoneExistForServiceId() {
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).insertService();
        uk.gov.pay.adminusers.fixtures.StripeAgreementDbFixture.stripeAgreementDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).withServiceId(service.getId()).insert();
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.StripeAgreementEntity> maybeStripeAgreementEntity = stripeAgreementDao.findByServiceExternalId("123");
        org.junit.jupiter.api.Assertions.assertFalse(maybeStripeAgreementEntity.isPresent());
    }
}
