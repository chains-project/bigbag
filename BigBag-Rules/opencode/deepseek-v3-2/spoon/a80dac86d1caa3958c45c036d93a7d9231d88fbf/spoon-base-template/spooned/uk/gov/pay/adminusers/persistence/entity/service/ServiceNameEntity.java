package uk.gov.pay.adminusers.persistence.entity.service;
// endregion
@javax.persistence.Entity
@javax.persistence.Table(name = "service_names")
public class ServiceNameEntity {
    @javax.persistence.Id
    @javax.persistence.SequenceGenerator(name = "service_names_id_seq", sequenceName = "service_names_id_seq", allocationSize = 1)
    @javax.persistence.GeneratedValue(strategy = javax.persistence.GenerationType.SEQUENCE, generator = "service_names_id_seq")
    private java.lang.Long id;

    @javax.persistence.JoinColumn(name = "service_id")
    @javax.persistence.ManyToOne(fetch = javax.persistence.FetchType.LAZY)
    private uk.gov.pay.adminusers.persistence.entity.ServiceEntity service;

    @javax.persistence.Column(name = "language")
    @javax.persistence.Enumerated(javax.persistence.EnumType.STRING)
    @javax.persistence.Convert(converter = uk.gov.service.payments.commons.model.SupportedLanguageJpaConverter.class)
    private uk.gov.service.payments.commons.model.SupportedLanguage language;

    @javax.persistence.Column(name = "name")
    private java.lang.String name;

    public ServiceNameEntity() {
        // make JPA happy
    }

    public static uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity from(uk.gov.service.payments.commons.model.SupportedLanguage language, java.lang.String name) {
        uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity entity = new uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity();
        entity.setLanguage(language);
        entity.setName(name);
        return entity;
    }

    // region <Getters/Setters>
    public java.lang.Long getId() {
        return id;
    }

    public void setId(java.lang.Long id) {
        this.id = id;
    }

    public uk.gov.pay.adminusers.persistence.entity.ServiceEntity getService() {
        return service;
    }

    public void setService(uk.gov.pay.adminusers.persistence.entity.ServiceEntity service) {
        this.service = service;
    }

    public uk.gov.service.payments.commons.model.SupportedLanguage getLanguage() {
        return language;
    }

    public void setLanguage(uk.gov.service.payments.commons.model.SupportedLanguage language) {
        this.language = language;
    }

    public java.lang.String getName() {
        return name;
    }

    public void setName(java.lang.String name) {
        this.name = name;
    }

    @java.lang.Override
    public boolean equals(java.lang.Object o) {
        if (this == o) {
            return true;
        }
        if ((o == null) || (getClass() != o.getClass())) {
            return false;
        }
        uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity that = ((uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity) (o));
        return (java.util.Objects.equals(service, that.service) && java.util.Objects.equals(language, that.language)) && java.util.Objects.equals(name, that.name);
    }

    @java.lang.Override
    public int hashCode() {
        return java.util.Objects.hash(service, language, name);
    }
}
