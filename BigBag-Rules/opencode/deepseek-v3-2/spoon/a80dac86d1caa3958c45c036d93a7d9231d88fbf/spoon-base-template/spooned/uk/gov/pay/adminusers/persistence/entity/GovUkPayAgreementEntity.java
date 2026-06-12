package uk.gov.pay.adminusers.persistence.entity;
@javax.persistence.Entity
@javax.persistence.Table(name = "govuk_pay_agreements")
public class GovUkPayAgreementEntity {
    @javax.persistence.Id
    @javax.persistence.GeneratedValue(strategy = javax.persistence.GenerationType.SEQUENCE, generator = "govuk_pay_agreements_id_seq_gen")
    @javax.persistence.SequenceGenerator(name = "govuk_pay_agreements_id_seq_gen", sequenceName = "govuk_pay_agreements_id_seq", allocationSize = 1)
    private java.lang.Integer id;

    @javax.persistence.Column(name = "email")
    private java.lang.String email;

    @javax.persistence.Column(name = "agreement_time", columnDefinition = "TIMESTAMP WITH TIME ZONE NOT NULL")
    @javax.persistence.Convert(converter = uk.gov.pay.adminusers.persistence.entity.UTCDateTimeConverter.class)
    private java.time.ZonedDateTime agreementTime;

    @javax.persistence.OneToOne
    @javax.persistence.JoinColumn(name = "service_id")
    private uk.gov.pay.adminusers.persistence.entity.ServiceEntity service;

    public GovUkPayAgreementEntity() {
        // for jpa
    }

    public GovUkPayAgreementEntity(java.lang.String email, java.time.ZonedDateTime agreementTime) {
        this.email = email;
        this.agreementTime = agreementTime;
    }

    public uk.gov.pay.adminusers.model.GovUkPayAgreement toGovUkPayAgreement() {
        return new uk.gov.pay.adminusers.model.GovUkPayAgreement(email, agreementTime);
    }

    public java.lang.Integer getId() {
        return id;
    }

    public java.lang.String getEmail() {
        return email;
    }

    public java.time.ZonedDateTime getAgreementTime() {
        return agreementTime;
    }

    public uk.gov.pay.adminusers.persistence.entity.ServiceEntity getService() {
        return service;
    }

    public void setService(uk.gov.pay.adminusers.persistence.entity.ServiceEntity service) {
        this.service = service;
    }
}
