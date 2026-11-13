package uk.gov.pay.adminusers.persistence.dao;
public class GovUkPayAgreementDaoIT extends uk.gov.pay.adminusers.persistence.dao.DaoTestBase {
    private uk.gov.pay.adminusers.persistence.dao.GovUkPayAgreementDao agreementDao;

    private uk.gov.pay.adminusers.persistence.dao.ServiceDao serviceDao;

    @org.junit.jupiter.api.BeforeEach
    public void setUp() {
        agreementDao = uk.gov.pay.adminusers.persistence.dao.DaoTestBase.env.getInstance(uk.gov.pay.adminusers.persistence.dao.GovUkPayAgreementDao.class);
        serviceDao = uk.gov.pay.adminusers.persistence.dao.DaoTestBase.env.getInstance(uk.gov.pay.adminusers.persistence.dao.ServiceDao.class);
    }

    @org.junit.jupiter.api.Test
    public void shouldPersistEntity() {
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).withGatewayAccountIds(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt().toString()).insertService();
        java.time.ZonedDateTime dateTime = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC);
        uk.gov.pay.adminusers.persistence.entity.GovUkPayAgreementEntity newEntity = new uk.gov.pay.adminusers.persistence.entity.GovUkPayAgreementEntity("someone@example.org", dateTime);
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.ServiceEntity> serviceEntity = serviceDao.findByExternalId(service.getExternalId());
        newEntity.setService(serviceEntity.get());
        agreementDao.persist(newEntity);
        java.util.List<java.util.Map<java.lang.String, java.lang.Object>> searchResults = uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper.findGovUkPayAgreementEntity(serviceEntity.get().getId());
        org.hamcrest.MatcherAssert.assertThat(searchResults.size(), org.hamcrest.CoreMatchers.is(1));
        org.hamcrest.MatcherAssert.assertThat(searchResults.get(0).get("service_id"), org.hamcrest.CoreMatchers.is(service.getId()));
        org.hamcrest.MatcherAssert.assertThat(searchResults.get(0).get("email"), org.hamcrest.CoreMatchers.is("someone@example.org"));
        org.hamcrest.MatcherAssert.assertThat(searchResults.get(0).get("agreement_time"), org.hamcrest.CoreMatchers.is(java.sql.Timestamp.from(dateTime.toInstant())));
    }

    @org.junit.jupiter.api.Test
    public void shouldNotPersistGovUkPayAgreementEntityWhenOneAlreadyExistsForService() {
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).withGatewayAccountIds(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt().toString()).insertService();
        java.time.ZonedDateTime dateTime = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC);
        uk.gov.pay.adminusers.persistence.entity.GovUkPayAgreementEntity newEntity = new uk.gov.pay.adminusers.persistence.entity.GovUkPayAgreementEntity("someone@example.org", dateTime);
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.ServiceEntity> serviceEntity = serviceDao.findByExternalId(service.getExternalId());
        newEntity.setService(serviceEntity.get());
        agreementDao.persist(newEntity);
        uk.gov.pay.adminusers.persistence.entity.GovUkPayAgreementEntity anotherEntity = new uk.gov.pay.adminusers.persistence.entity.GovUkPayAgreementEntity("someone.else@example.org", java.time.ZonedDateTime.now());
        anotherEntity.setService(serviceEntity.get());
        javax.persistence.RollbackException rollbackException = org.junit.jupiter.api.Assertions.assertThrows(javax.persistence.RollbackException.class, () -> agreementDao.persist(anotherEntity));
        org.hamcrest.MatcherAssert.assertThat(rollbackException.getMessage(), org.hamcrest.CoreMatchers.containsString(("Key (service_id)=(" + serviceEntity.get().getId()) + ") already exists."));
    }

    @org.junit.jupiter.api.Test
    public void shouldNotFindStripeAgreementEntityWhenNoneExistForServiceId() {
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.fixtures.ServiceDbFixture.serviceDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).withExternalId("abcde1234").insertService();
        uk.gov.pay.adminusers.fixtures.GovUkPayAgreementDbFixture.govUkPayAgreementDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).withServiceId(service.getId()).insert();
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.GovUkPayAgreementEntity> maybeEntity = agreementDao.findByExternalServiceId("abcd1235");
        org.hamcrest.MatcherAssert.assertThat(maybeEntity.isPresent(), org.hamcrest.CoreMatchers.is(false));
    }
}
