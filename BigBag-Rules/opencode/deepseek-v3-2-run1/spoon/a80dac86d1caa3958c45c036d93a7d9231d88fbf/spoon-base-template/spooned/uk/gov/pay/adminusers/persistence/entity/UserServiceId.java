package uk.gov.pay.adminusers.persistence.entity;
@javax.persistence.Embeddable
public class UserServiceId implements java.io.Serializable {
    @javax.persistence.Column(name = "service_id", nullable = false)
    private java.lang.Integer serviceId;

    @javax.persistence.Column(name = "user_id", nullable = false)
    private java.lang.Integer userId;

    public java.lang.Integer getServiceId() {
        return serviceId;
    }

    public void setServiceId(java.lang.Integer serviceId) {
        this.serviceId = serviceId;
    }

    public java.lang.Integer getUserId() {
        return userId;
    }

    public void setUserId(java.lang.Integer userId) {
        this.userId = userId;
    }
}
