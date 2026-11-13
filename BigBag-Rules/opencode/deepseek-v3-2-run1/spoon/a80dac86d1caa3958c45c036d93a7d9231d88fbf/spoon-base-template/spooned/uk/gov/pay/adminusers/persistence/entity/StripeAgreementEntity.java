package uk.gov.pay.adminusers.persistence.entity;
@javax.persistence.Entity
@javax.persistence.Table(name = "stripe_agreements")
@javax.persistence.SequenceGenerator(name = "stripe_agreements_id_seq_gen", sequenceName = "stripe_agreements_id_seq", allocationSize = 1)
public class StripeAgreementEntity {
    @javax.persistence.Id
    @javax.persistence.GeneratedValue(strategy = javax.persistence.GenerationType.SEQUENCE, generator = "stripe_agreements_id_seq_gen")
    private int id;

    @javax.persistence.OneToOne
    @javax.persistence.JoinColumn(name = "service_id", referencedColumnName = "id")
    private uk.gov.pay.adminusers.persistence.entity.ServiceEntity service;

    @javax.persistence.Column(name = "ip_address")
    private java.lang.String ipAddress;

    @javax.persistence.Column(name = "agreement_time", columnDefinition = "TIMESTAMP WITH TIME ZONE NOT NULL")
    @javax.persistence.Convert(converter = uk.gov.pay.adminusers.persistence.entity.UTCDateTimeConverter.class)
    private java.time.ZonedDateTime agreementTime;

    public StripeAgreementEntity() {
        // for jpa
    }

    public StripeAgreementEntity(uk.gov.pay.adminusers.persistence.entity.ServiceEntity service, java.lang.String ipAddress, java.time.ZonedDateTime agreementTime) {
        this.service = service;
        this.ipAddress = ipAddress;
        this.agreementTime = agreementTime;
    }

    public uk.gov.pay.adminusers.model.StripeAgreement toStripeAgreement() {
        try {
            return new uk.gov.pay.adminusers.model.StripeAgreement(java.net.InetAddress.getByName(ipAddress), agreementTime);
        } catch (java.net.UnknownHostException e) {
            // IP addresses are validated before storing them in the table so it’s very unlikely this will happen
            throw new java.lang.RuntimeException(java.lang.String.format("%s is not a valid InetAddress.", ipAddress), e);
        }
    }

    public int getId() {
        return id;
    }

    public uk.gov.pay.adminusers.persistence.entity.ServiceEntity getService() {
        return service;
    }

    public java.lang.String getIpAddress() {
        return ipAddress;
    }

    public java.time.ZonedDateTime getAgreementTime() {
        return agreementTime;
    }
}
