package uk.gov.pay.adminusers.persistence.entity;
public class InviteTypeConverterTest {
    private final uk.gov.pay.adminusers.persistence.entity.InviteTypeConverter inviteTypeConverter = new uk.gov.pay.adminusers.persistence.entity.InviteTypeConverter();

    @org.junit.jupiter.api.Test
    public void userEnumConstantConvertToDatabaseColumnReturnsUserString() {
        java.lang.String databaseColumnValue = inviteTypeConverter.convertToDatabaseColumn(uk.gov.pay.adminusers.model.InviteType.USER);
        org.hamcrest.MatcherAssert.assertThat(databaseColumnValue, org.hamcrest.Matchers.is("user"));
    }

    @org.junit.jupiter.api.Test
    public void serviceEnumConstantConvertToDatabaseColumnReturnsServiceString() {
        java.lang.String databaseColumnValue = inviteTypeConverter.convertToDatabaseColumn(uk.gov.pay.adminusers.model.InviteType.SERVICE);
        org.hamcrest.MatcherAssert.assertThat(databaseColumnValue, org.hamcrest.Matchers.is("service"));
    }

    @org.junit.jupiter.api.Test
    public void userStringConvertToEntityAttributeReturnsUserEnumConstant() {
        uk.gov.pay.adminusers.model.InviteType entityAttribute = inviteTypeConverter.convertToEntityAttribute("user");
        org.hamcrest.MatcherAssert.assertThat(entityAttribute, org.hamcrest.Matchers.is(uk.gov.pay.adminusers.model.InviteType.USER));
    }

    @org.junit.jupiter.api.Test
    public void serviceStringConvertToEntityAttributeReturnsServiceEnumConstant() {
        uk.gov.pay.adminusers.model.InviteType entityAttribute = inviteTypeConverter.convertToEntityAttribute("service");
        org.hamcrest.MatcherAssert.assertThat(entityAttribute, org.hamcrest.Matchers.is(uk.gov.pay.adminusers.model.InviteType.SERVICE));
    }

    @org.junit.jupiter.api.Test
    public void unhandledStringConvertToEntityAttributeThrowsException() {
        org.junit.jupiter.api.Assertions.assertThrows(java.lang.RuntimeException.class, () -> inviteTypeConverter.convertToEntityAttribute("Someone went wild in the DB!"));
    }
}
