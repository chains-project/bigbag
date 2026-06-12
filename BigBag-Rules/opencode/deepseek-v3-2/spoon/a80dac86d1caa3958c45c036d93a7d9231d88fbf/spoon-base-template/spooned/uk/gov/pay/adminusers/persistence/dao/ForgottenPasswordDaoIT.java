package uk.gov.pay.adminusers.persistence.dao;
public class ForgottenPasswordDaoIT extends uk.gov.pay.adminusers.persistence.dao.DaoTestBase {
    private uk.gov.pay.adminusers.persistence.dao.UserDao userDao;

    private uk.gov.pay.adminusers.persistence.dao.ForgottenPasswordDao forgottenPasswordDao;

    @org.junit.jupiter.api.BeforeEach
    public void before() {
        userDao = uk.gov.pay.adminusers.persistence.dao.DaoTestBase.env.getInstance(uk.gov.pay.adminusers.persistence.dao.UserDao.class);
        forgottenPasswordDao = uk.gov.pay.adminusers.persistence.dao.DaoTestBase.env.getInstance(uk.gov.pay.adminusers.persistence.dao.ForgottenPasswordDao.class);
    }

    @org.junit.jupiter.api.Test
    public void shouldPersistAForgottenPasswordEntity() {
        java.lang.String forgottenPasswordCode = org.apache.commons.lang3.RandomStringUtils.random(10);
        java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email = username + "@example.com";
        uk.gov.pay.adminusers.model.User user = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).withUsername(username).withEmail(email).insertUser();
        java.lang.String userExternalId = user.getExternalId();
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = userDao.findByExternalId(userExternalId).get();
        uk.gov.pay.adminusers.model.ForgottenPassword forgottenPassword = uk.gov.pay.adminusers.model.ForgottenPassword.forgottenPassword(forgottenPasswordCode, userExternalId);
        uk.gov.pay.adminusers.persistence.entity.ForgottenPasswordEntity forgottenPasswordEntity = uk.gov.pay.adminusers.persistence.entity.ForgottenPasswordEntity.from(forgottenPassword, userEntity);
        forgottenPasswordDao.persist(forgottenPasswordEntity);
        org.hamcrest.MatcherAssert.assertThat(forgottenPasswordEntity.getId(), org.hamcrest.core.Is.is(org.hamcrest.core.IsNull.notNullValue()));
        java.util.List<java.util.Map<java.lang.String, java.lang.Object>> forgottenPasswordById = uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper.findForgottenPasswordById(forgottenPasswordEntity.getId());
        org.hamcrest.MatcherAssert.assertThat(forgottenPasswordById.size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(forgottenPasswordById.get(0).get("code"), org.hamcrest.core.Is.is(forgottenPasswordCode));
        java.sql.Timestamp storedDate = ((java.sql.Timestamp) (forgottenPasswordById.get(0).get("date")));
        java.time.ZonedDateTime storedDateTime = java.time.ZonedDateTime.ofInstant(storedDate.toInstant(), java.time.ZoneId.of("UTC"));
        org.hamcrest.MatcherAssert.assertThat(storedDateTime, org.exparity.hamcrest.date.ZonedDateTimeMatchers.within(1, java.time.temporal.ChronoUnit.MINUTES, forgottenPassword.getDate()));
    }

    @org.junit.jupiter.api.Test
    public void shouldFindForgottenPasswordByCode_ifNotExpired() {
        java.lang.String forgottenPasswordCode = org.apache.commons.lang3.RandomStringUtils.random(10);
        java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email = username + "@example.com";
        uk.gov.pay.adminusers.model.User user = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).withUsername(username).withEmail(email).insertUser();
        java.lang.String userExternalId = user.getExternalId();
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = userDao.findByExternalId(userExternalId).get();
        java.time.ZonedDateTime notExpired = java.time.ZonedDateTime.now().minusMinutes(89);
        uk.gov.pay.adminusers.model.ForgottenPassword forgottenPassword = uk.gov.pay.adminusers.model.ForgottenPassword.forgottenPassword(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt(), forgottenPasswordCode, notExpired, userExternalId);
        uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper.add(forgottenPassword, userEntity.getId());
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.ForgottenPasswordEntity> forgottenPasswordEntityOptional = forgottenPasswordDao.findNonExpiredByCode(forgottenPassword.getCode());
        junit.framework.TestCase.assertTrue(forgottenPasswordEntityOptional.isPresent());
        uk.gov.pay.adminusers.persistence.entity.ForgottenPasswordEntity forgottenPasswordEntity = forgottenPasswordEntityOptional.get();
        org.hamcrest.MatcherAssert.assertThat(forgottenPasswordEntity.getCode(), org.hamcrest.core.Is.is(forgottenPassword.getCode()));
        org.hamcrest.MatcherAssert.assertThat(forgottenPasswordEntity.getDate(), org.exparity.hamcrest.date.ZonedDateTimeMatchers.within(1, java.time.temporal.ChronoUnit.MINUTES, forgottenPassword.getDate()));
    }

    @org.junit.jupiter.api.Test
    public void shouldNotFindForgottenPasswordByCode_ifExpired() {
        java.lang.String forgottenPasswordCode = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email = username + "@example.com";
        uk.gov.pay.adminusers.model.User user = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).withUsername(username).withEmail(email).insertUser();
        java.lang.String userExternalId = user.getExternalId();
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = userDao.findByExternalId(userExternalId).get();
        java.time.ZonedDateTime expired = java.time.ZonedDateTime.now().minusMinutes(91);
        uk.gov.pay.adminusers.model.ForgottenPassword forgottenPassword = uk.gov.pay.adminusers.model.ForgottenPassword.forgottenPassword(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt(), forgottenPasswordCode, expired, userExternalId);
        uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper.add(forgottenPassword, userEntity.getId());
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.ForgottenPasswordEntity> forgottenPasswordEntityOptional = forgottenPasswordDao.findNonExpiredByCode(forgottenPassword.getCode());
        junit.framework.TestCase.assertFalse(forgottenPasswordEntityOptional.isPresent());
    }

    @org.junit.jupiter.api.Test
    public void shouldRemoveForgottenPasswordEntity() {
        java.lang.String forgottenPasswordCode = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String username = uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid();
        java.lang.String email = username + "@example.com";
        uk.gov.pay.adminusers.model.User user = uk.gov.pay.adminusers.fixtures.UserDbFixture.userDbFixture(uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper).withUsername(username).withEmail(email).insertUser();
        java.lang.String userExternalId = user.getExternalId();
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = userDao.findByExternalId(userExternalId).get();
        java.time.ZonedDateTime notExpired = java.time.ZonedDateTime.now().minusMinutes(89);
        uk.gov.pay.adminusers.model.ForgottenPassword forgottenPassword = uk.gov.pay.adminusers.model.ForgottenPassword.forgottenPassword(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt(), forgottenPasswordCode, notExpired, userExternalId);
        uk.gov.pay.adminusers.persistence.dao.DaoTestBase.databaseHelper.add(forgottenPassword, userEntity.getId());
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.ForgottenPasswordEntity> forgottenPasswordEntityOptional = forgottenPasswordDao.findNonExpiredByCode(forgottenPassword.getCode());
        junit.framework.TestCase.assertTrue(forgottenPasswordEntityOptional.isPresent());
        forgottenPasswordDao.remove(forgottenPasswordEntityOptional.get());
        org.hamcrest.MatcherAssert.assertThat(forgottenPasswordDao.findNonExpiredByCode(forgottenPassword.getCode()).isPresent(), org.hamcrest.core.Is.is(false));
    }
}
