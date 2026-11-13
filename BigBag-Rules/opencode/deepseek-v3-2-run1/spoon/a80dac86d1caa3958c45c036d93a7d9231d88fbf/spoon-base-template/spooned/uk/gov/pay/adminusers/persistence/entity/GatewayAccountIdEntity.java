package uk.gov.pay.adminusers.persistence.entity;
@javax.persistence.Entity
@javax.persistence.Table(name = "service_gateway_accounts")
@javax.persistence.SequenceGenerator(name = "service_gatewayAccounts_seq_gen", sequenceName = "service_gateway_accounts_id_seq", allocationSize = 1)
public class GatewayAccountIdEntity {
    @javax.persistence.Id
    @javax.persistence.GeneratedValue(strategy = javax.persistence.GenerationType.SEQUENCE, generator = "service_gatewayAccounts_seq_gen")
    private java.lang.Long id;

    @javax.persistence.Column(name = "gateway_account_id")
    private java.lang.String gatewayAccountId;

    @javax.persistence.ManyToOne(fetch = javax.persistence.FetchType.LAZY)
    @javax.persistence.JoinColumn(name = "service_id")
    private uk.gov.pay.adminusers.persistence.entity.ServiceEntity service;

    public GatewayAccountIdEntity() {
    }

    public GatewayAccountIdEntity(java.lang.String gatewayAccountId, uk.gov.pay.adminusers.persistence.entity.ServiceEntity service) {
        this.gatewayAccountId = gatewayAccountId;
        this.service = service;
    }

    public java.lang.Long getId() {
        return id;
    }

    public void setId(java.lang.Long id) {
        this.id = id;
    }

    public java.lang.String getGatewayAccountId() {
        return gatewayAccountId;
    }

    public void setGatewayAccountId(java.lang.String gatewayAccountId) {
        this.gatewayAccountId = gatewayAccountId;
    }

    public uk.gov.pay.adminusers.persistence.entity.ServiceEntity getService() {
        return service;
    }

    public void setService(uk.gov.pay.adminusers.persistence.entity.ServiceEntity service) {
        this.service = service;
    }
}
