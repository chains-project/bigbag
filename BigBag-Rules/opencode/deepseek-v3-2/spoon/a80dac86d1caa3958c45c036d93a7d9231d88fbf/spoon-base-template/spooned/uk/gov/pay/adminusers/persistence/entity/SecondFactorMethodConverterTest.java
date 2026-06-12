package uk.gov.pay.adminusers.persistence.entity;
public class SecondFactorMethodConverterTest {
    private final uk.gov.pay.adminusers.persistence.entity.SecondFactorMethodConverter secondFactorMethodConverter = new uk.gov.pay.adminusers.persistence.entity.SecondFactorMethodConverter();

    @org.junit.jupiter.api.Test
    public void smsEnumVariantConvertToDatabaseColumnReturnsSmsString() {
        java.lang.String databaseColumnValue = secondFactorMethodConverter.convertToDatabaseColumn(uk.gov.pay.adminusers.model.SecondFactorMethod.SMS);
        org.hamcrest.MatcherAssert.assertThat(databaseColumnValue, org.hamcrest.Matchers.is("sms"));
    }

    @org.junit.jupiter.api.Test
    public void appEnumVariantConvertToDatabaseColumnReturnsAppString() {
        java.lang.String databaseColumnValue = secondFactorMethodConverter.convertToDatabaseColumn(uk.gov.pay.adminusers.model.SecondFactorMethod.APP);
        org.hamcrest.MatcherAssert.assertThat(databaseColumnValue, org.hamcrest.Matchers.is("app"));
    }

    @org.junit.jupiter.api.Test
    public void smsStringConvertToEntityAttributeReturnsSmsEnumVariant() {
        uk.gov.pay.adminusers.model.SecondFactorMethod entityAttribute = secondFactorMethodConverter.convertToEntityAttribute("sms");
        org.hamcrest.MatcherAssert.assertThat(entityAttribute, org.hamcrest.Matchers.is(uk.gov.pay.adminusers.model.SecondFactorMethod.SMS));
    }

    @org.junit.jupiter.api.Test
    public void appStringConvertToEntityAttributeReturnsAppEnumVariant() {
        uk.gov.pay.adminusers.model.SecondFactorMethod entityAttribute = secondFactorMethodConverter.convertToEntityAttribute("app");
        org.hamcrest.MatcherAssert.assertThat(entityAttribute, org.hamcrest.Matchers.is(uk.gov.pay.adminusers.model.SecondFactorMethod.APP));
    }

    @org.junit.jupiter.api.Test
    public void unhandledStringConvertToEntityAttributeReturnsSmsEnumVariant() {
        uk.gov.pay.adminusers.model.SecondFactorMethod entityAttribute = secondFactorMethodConverter.convertToEntityAttribute("Someone went wild in the DB!");
        org.hamcrest.MatcherAssert.assertThat(entityAttribute, org.hamcrest.Matchers.is(uk.gov.pay.adminusers.model.SecondFactorMethod.SMS));
    }
}
