package uk.gov.pay.adminusers.model;
public class ServiceName {
    private final java.lang.String englishServiceName;

    private final java.util.Map<uk.gov.service.payments.commons.model.SupportedLanguage, java.lang.String> translatedServiceNames;

    public ServiceName(java.lang.String englishServiceName) {
        this(englishServiceName, java.util.Collections.emptyMap());
    }

    public ServiceName(java.lang.String englishServiceName, java.util.Map<uk.gov.service.payments.commons.model.SupportedLanguage, java.lang.String> translatedServiceNames) {
        if (translatedServiceNames.containsKey(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH)) {
            throw new java.lang.IllegalArgumentException("Specify the English service name as the first argument only and not in the map please");
        }
        this.englishServiceName = java.util.Objects.requireNonNull(englishServiceName);
        this.translatedServiceNames = translatedServiceNames.entrySet().stream().filter(entry -> !entry.getValue().isBlank()).collect(java.util.stream.Collectors.toUnmodifiableMap(java.util.Map.Entry::getKey, java.util.Map.Entry::getValue));
    }

    public java.lang.String getEnglish() {
        return englishServiceName;
    }

    public java.util.Map<uk.gov.service.payments.commons.model.SupportedLanguage, java.lang.String> getEnglishAndTranslations() {
        return java.util.stream.Stream.concat(java.util.Map.of(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, englishServiceName).entrySet().stream(), translatedServiceNames.entrySet().stream()).collect(java.util.stream.Collectors.toUnmodifiableMap(java.util.Map.Entry::getKey, java.util.Map.Entry::getValue));
    }

    public static uk.gov.pay.adminusers.model.ServiceName from(java.util.Collection<uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity> serviceNameEntities) {
        java.lang.String englishServiceName = serviceNameEntities.stream().filter(serviceNameEntity -> serviceNameEntity.getLanguage() == uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH).findFirst().map(uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity::getName).orElseThrow(() -> new java.lang.IllegalArgumentException("No English-language service name provided"));
        java.util.Map<uk.gov.service.payments.commons.model.SupportedLanguage, java.lang.String> translatedServiceNames = serviceNameEntities.stream().filter(serviceNameEntity -> serviceNameEntity.getLanguage() != uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH).collect(java.util.stream.Collectors.toUnmodifiableMap(uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity::getLanguage, uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity::getName));
        return new uk.gov.pay.adminusers.model.ServiceName(englishServiceName, translatedServiceNames);
    }
}
