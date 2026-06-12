package uk.gov.pay.adminusers.model;
public class ServiceNameTest {
    private static final java.lang.String ENGLISH_SERVICE_NAME = "Apply for your licence";

    private static final java.lang.String WELSH_SERVICE_NAME = "Gwneud cais am eich trwydded";

    @org.junit.jupiter.api.Test
    public void shouldCreateWithJustEnglishServiceName() {
        uk.gov.pay.adminusers.model.ServiceName serviceName = new uk.gov.pay.adminusers.model.ServiceName(uk.gov.pay.adminusers.model.ServiceNameTest.ENGLISH_SERVICE_NAME);
        org.hamcrest.MatcherAssert.assertThat(serviceName.getEnglish(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.ServiceNameTest.ENGLISH_SERVICE_NAME));
        org.hamcrest.MatcherAssert.assertThat(serviceName.getEnglishAndTranslations().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(serviceName.getEnglishAndTranslations().get(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.ServiceNameTest.ENGLISH_SERVICE_NAME));
    }

    @org.junit.jupiter.api.Test
    public void shouldCreateWithEnglishAndNonEnglishServiceName() {
        uk.gov.pay.adminusers.model.ServiceName serviceName = new uk.gov.pay.adminusers.model.ServiceName(uk.gov.pay.adminusers.model.ServiceNameTest.ENGLISH_SERVICE_NAME, java.util.Map.of(uk.gov.service.payments.commons.model.SupportedLanguage.WELSH, uk.gov.pay.adminusers.model.ServiceNameTest.WELSH_SERVICE_NAME));
        org.hamcrest.MatcherAssert.assertThat(serviceName.getEnglish(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.ServiceNameTest.ENGLISH_SERVICE_NAME));
        org.hamcrest.MatcherAssert.assertThat(serviceName.getEnglishAndTranslations().size(), org.hamcrest.core.Is.is(2));
        org.hamcrest.MatcherAssert.assertThat(serviceName.getEnglishAndTranslations().get(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.ServiceNameTest.ENGLISH_SERVICE_NAME));
        org.hamcrest.MatcherAssert.assertThat(serviceName.getEnglishAndTranslations().get(uk.gov.service.payments.commons.model.SupportedLanguage.WELSH), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.ServiceNameTest.WELSH_SERVICE_NAME));
    }

    @org.junit.jupiter.api.Test
    public void shouldIgnoreNonEnglishServiceNameIfEmpty() {
        uk.gov.pay.adminusers.model.ServiceName serviceName = new uk.gov.pay.adminusers.model.ServiceName(uk.gov.pay.adminusers.model.ServiceNameTest.ENGLISH_SERVICE_NAME, java.util.Map.of(uk.gov.service.payments.commons.model.SupportedLanguage.WELSH, ""));
        org.hamcrest.MatcherAssert.assertThat(serviceName.getEnglish(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.ServiceNameTest.ENGLISH_SERVICE_NAME));
        org.hamcrest.MatcherAssert.assertThat(serviceName.getEnglishAndTranslations().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(serviceName.getEnglishAndTranslations().get(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.ServiceNameTest.ENGLISH_SERVICE_NAME));
        org.hamcrest.MatcherAssert.assertThat(serviceName.getEnglishAndTranslations().get(uk.gov.service.payments.commons.model.SupportedLanguage.WELSH), org.hamcrest.core.Is.is(org.hamcrest.core.IsNull.nullValue()));
    }

    @org.junit.jupiter.api.Test
    public void shouldIgnoreNonEnglishServiceNameIfBlank() {
        uk.gov.pay.adminusers.model.ServiceName serviceName = new uk.gov.pay.adminusers.model.ServiceName(uk.gov.pay.adminusers.model.ServiceNameTest.ENGLISH_SERVICE_NAME, java.util.Map.of(uk.gov.service.payments.commons.model.SupportedLanguage.WELSH, "  "));
        org.hamcrest.MatcherAssert.assertThat(serviceName.getEnglish(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.ServiceNameTest.ENGLISH_SERVICE_NAME));
        org.hamcrest.MatcherAssert.assertThat(serviceName.getEnglishAndTranslations().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(serviceName.getEnglishAndTranslations().get(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.ServiceNameTest.ENGLISH_SERVICE_NAME));
        org.hamcrest.MatcherAssert.assertThat(serviceName.getEnglishAndTranslations().get(uk.gov.service.payments.commons.model.SupportedLanguage.WELSH), org.hamcrest.core.Is.is(org.hamcrest.core.IsNull.nullValue()));
    }

    @org.junit.jupiter.api.Test
    public void shouldThrowIfNullEnglishServiceName() {
        org.junit.jupiter.api.Assertions.assertThrows(java.lang.NullPointerException.class, () -> new uk.gov.pay.adminusers.model.ServiceName(null));
    }

    @org.junit.jupiter.api.Test
    public void shouldThrowIfEnglishServiceNameIncludedInMap() {
        org.junit.jupiter.api.Assertions.assertThrows(java.lang.IllegalArgumentException.class, () -> new uk.gov.pay.adminusers.model.ServiceName(uk.gov.pay.adminusers.model.ServiceNameTest.ENGLISH_SERVICE_NAME, java.util.Map.of(uk.gov.service.payments.commons.model.SupportedLanguage.WELSH, uk.gov.pay.adminusers.model.ServiceNameTest.WELSH_SERVICE_NAME, uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, uk.gov.pay.adminusers.model.ServiceNameTest.ENGLISH_SERVICE_NAME)));
    }

    @org.junit.jupiter.api.Test
    public void shouldConvertFromEnglishServiceNameEntity() {
        uk.gov.pay.adminusers.model.ServiceName serviceName = uk.gov.pay.adminusers.model.ServiceName.from(java.util.List.of(uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity.from(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, uk.gov.pay.adminusers.model.ServiceNameTest.ENGLISH_SERVICE_NAME)));
        org.hamcrest.MatcherAssert.assertThat(serviceName.getEnglish(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.ServiceNameTest.ENGLISH_SERVICE_NAME));
        org.hamcrest.MatcherAssert.assertThat(serviceName.getEnglishAndTranslations().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(serviceName.getEnglishAndTranslations().get(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.ServiceNameTest.ENGLISH_SERVICE_NAME));
    }

    @org.junit.jupiter.api.Test
    public void shouldConvertFromEnglishServiceNameEntityAndNonEnglishServiceNameEntity() {
        uk.gov.pay.adminusers.model.ServiceName serviceName = uk.gov.pay.adminusers.model.ServiceName.from(java.util.List.of(uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity.from(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, uk.gov.pay.adminusers.model.ServiceNameTest.ENGLISH_SERVICE_NAME), uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity.from(uk.gov.service.payments.commons.model.SupportedLanguage.WELSH, uk.gov.pay.adminusers.model.ServiceNameTest.WELSH_SERVICE_NAME)));
        org.hamcrest.MatcherAssert.assertThat(serviceName.getEnglish(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.ServiceNameTest.ENGLISH_SERVICE_NAME));
        org.hamcrest.MatcherAssert.assertThat(serviceName.getEnglishAndTranslations().size(), org.hamcrest.core.Is.is(2));
        org.hamcrest.MatcherAssert.assertThat(serviceName.getEnglishAndTranslations().get(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.ServiceNameTest.ENGLISH_SERVICE_NAME));
        org.hamcrest.MatcherAssert.assertThat(serviceName.getEnglishAndTranslations().get(uk.gov.service.payments.commons.model.SupportedLanguage.WELSH), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.ServiceNameTest.WELSH_SERVICE_NAME));
    }

    @org.junit.jupiter.api.Test
    public void shouldConvertFromServiceNameEntitiesIgnoringNonEnglishServiceNameIfEmpty() {
        uk.gov.pay.adminusers.model.ServiceName serviceName = uk.gov.pay.adminusers.model.ServiceName.from(java.util.List.of(uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity.from(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, uk.gov.pay.adminusers.model.ServiceNameTest.ENGLISH_SERVICE_NAME), uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity.from(uk.gov.service.payments.commons.model.SupportedLanguage.WELSH, "")));
        org.hamcrest.MatcherAssert.assertThat(serviceName.getEnglish(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.ServiceNameTest.ENGLISH_SERVICE_NAME));
        org.hamcrest.MatcherAssert.assertThat(serviceName.getEnglishAndTranslations().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(serviceName.getEnglishAndTranslations().get(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.ServiceNameTest.ENGLISH_SERVICE_NAME));
        org.hamcrest.MatcherAssert.assertThat(serviceName.getEnglishAndTranslations().get(uk.gov.service.payments.commons.model.SupportedLanguage.WELSH), org.hamcrest.core.Is.is(org.hamcrest.core.IsNull.nullValue()));
    }

    @org.junit.jupiter.api.Test
    public void shouldConvertFromServiceNameEntitiesIgnoringNonEnglishServiceNameIfBlank() {
        uk.gov.pay.adminusers.model.ServiceName serviceName = uk.gov.pay.adminusers.model.ServiceName.from(java.util.List.of(uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity.from(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, uk.gov.pay.adminusers.model.ServiceNameTest.ENGLISH_SERVICE_NAME), uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity.from(uk.gov.service.payments.commons.model.SupportedLanguage.WELSH, "  ")));
        org.hamcrest.MatcherAssert.assertThat(serviceName.getEnglish(), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.ServiceNameTest.ENGLISH_SERVICE_NAME));
        org.hamcrest.MatcherAssert.assertThat(serviceName.getEnglishAndTranslations().size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(serviceName.getEnglishAndTranslations().get(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH), org.hamcrest.core.Is.is(uk.gov.pay.adminusers.model.ServiceNameTest.ENGLISH_SERVICE_NAME));
        org.hamcrest.MatcherAssert.assertThat(serviceName.getEnglishAndTranslations().get(uk.gov.service.payments.commons.model.SupportedLanguage.WELSH), org.hamcrest.core.Is.is(org.hamcrest.core.IsNull.nullValue()));
    }

    @org.junit.jupiter.api.Test
    public void shouldThrowIfWhenConvertingFromServiceNameEntitiesIfNoEnglishServiceName() {
        org.junit.jupiter.api.Assertions.assertThrows(java.lang.IllegalArgumentException.class, () -> uk.gov.pay.adminusers.model.ServiceName.from(java.util.List.of(uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity.from(uk.gov.service.payments.commons.model.SupportedLanguage.WELSH, "  "))));
    }

    @org.junit.jupiter.api.Test
    public void shouldReturnUnmodifiableMap() {
        uk.gov.pay.adminusers.model.ServiceName serviceName = new uk.gov.pay.adminusers.model.ServiceName(uk.gov.pay.adminusers.model.ServiceNameTest.ENGLISH_SERVICE_NAME, java.util.Map.of(uk.gov.service.payments.commons.model.SupportedLanguage.WELSH, uk.gov.pay.adminusers.model.ServiceNameTest.WELSH_SERVICE_NAME));
        org.junit.jupiter.api.Assertions.assertThrows(java.lang.UnsupportedOperationException.class, () -> serviceName.getEnglishAndTranslations().put(uk.gov.service.payments.commons.model.SupportedLanguage.WELSH, "Sneakily try to add something"));
    }
}
