package uk.gov.pay.adminusers.persistence.entity;
public class ServiceEntityTest {
    private static final java.lang.String ENGLISH_SERVICE_NAME = "Apply for your licence";

    private static final java.lang.String WELSH_SERVICE_NAME = "Gwneud cais am eich trwydded";

    @org.junit.jupiter.api.Test
    public void shouldUpdateExistingServiceName() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().build();
        org.hamcrest.MatcherAssert.assertThat(serviceEntity.getServiceNames().size(), org.hamcrest.CoreMatchers.is(1));
        org.hamcrest.MatcherAssert.assertThat(serviceEntity.getServiceNames().get(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH).getName(), org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.model.Service.DEFAULT_NAME_VALUE));
        serviceEntity.addOrUpdateServiceName(uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity.from(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, uk.gov.pay.adminusers.persistence.entity.ServiceEntityTest.ENGLISH_SERVICE_NAME));
        org.hamcrest.MatcherAssert.assertThat(serviceEntity.getServiceNames().size(), org.hamcrest.CoreMatchers.is(1));
        org.hamcrest.MatcherAssert.assertThat(serviceEntity.getServiceNames().get(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH).getName(), org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.persistence.entity.ServiceEntityTest.ENGLISH_SERVICE_NAME));
    }

    @org.junit.jupiter.api.Test
    public void shouldAddNewServiceName() {
        uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().build();
        org.hamcrest.MatcherAssert.assertThat(serviceEntity.getServiceNames().size(), org.hamcrest.CoreMatchers.is(1));
        org.hamcrest.MatcherAssert.assertThat(serviceEntity.getServiceNames().get(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH).getName(), org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.model.Service.DEFAULT_NAME_VALUE));
        serviceEntity.addOrUpdateServiceName(uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity.from(uk.gov.service.payments.commons.model.SupportedLanguage.WELSH, uk.gov.pay.adminusers.persistence.entity.ServiceEntityTest.WELSH_SERVICE_NAME));
        org.hamcrest.MatcherAssert.assertThat(serviceEntity.getServiceNames().size(), org.hamcrest.CoreMatchers.is(2));
        org.hamcrest.MatcherAssert.assertThat(serviceEntity.getServiceNames().get(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH).getName(), org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.model.Service.DEFAULT_NAME_VALUE));
        org.hamcrest.MatcherAssert.assertThat(serviceEntity.getServiceNames().get(uk.gov.service.payments.commons.model.SupportedLanguage.WELSH).getName(), org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.persistence.entity.ServiceEntityTest.WELSH_SERVICE_NAME));
    }

    @org.junit.jupiter.api.Test
    public void shouldCreateEntity_withNotStartedAsDefault() {
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.persistence.entity.ServiceEntityBuilder.aServiceEntity().build().toService();
        org.hamcrest.MatcherAssert.assertThat(service.getGoLiveStage(), org.hamcrest.CoreMatchers.is(uk.gov.pay.adminusers.model.GoLiveStage.NOT_STARTED));
    }
}
