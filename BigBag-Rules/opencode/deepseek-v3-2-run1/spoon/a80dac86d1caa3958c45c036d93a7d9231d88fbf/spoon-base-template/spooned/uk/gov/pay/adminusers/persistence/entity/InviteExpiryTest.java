package uk.gov.pay.adminusers.persistence.entity;
public class InviteExpiryTest {
    private uk.gov.pay.adminusers.persistence.entity.InviteEntity inviteEntity;

    @org.junit.jupiter.api.BeforeEach
    public void setUp() {
        inviteEntity = anInvite();
    }

    @org.junit.jupiter.api.Test
    public void isExpired_shouldNotBeExpire_whenRecentlyCreated() {
        org.hamcrest.MatcherAssert.assertThat(inviteEntity.isExpired(), org.hamcrest.core.Is.is(false));
    }

    @org.junit.jupiter.api.Test
    public void isExpired_shouldBeExpire_whenExpiryDateIsInThePast() {
        inviteEntity.setExpiryDate(java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")).minus(1, java.time.temporal.ChronoUnit.SECONDS));
        org.hamcrest.MatcherAssert.assertThat(inviteEntity.isExpired(), org.hamcrest.core.Is.is(true));
    }

    private uk.gov.pay.adminusers.persistence.entity.InviteEntity anInvite() {
        uk.gov.pay.adminusers.persistence.entity.InviteEntity inviteEntity = new uk.gov.pay.adminusers.persistence.entity.InviteEntity("user@example.com", "code", "otpKey", new uk.gov.pay.adminusers.persistence.entity.RoleEntity());
        inviteEntity.setService(new uk.gov.pay.adminusers.persistence.entity.ServiceEntity());
        inviteEntity.setSender(new uk.gov.pay.adminusers.persistence.entity.UserEntity());
        return inviteEntity;
    }
}
