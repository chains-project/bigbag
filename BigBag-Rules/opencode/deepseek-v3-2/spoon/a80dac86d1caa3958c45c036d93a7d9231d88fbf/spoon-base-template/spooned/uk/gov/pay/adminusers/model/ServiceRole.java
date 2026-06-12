package uk.gov.pay.adminusers.model;
@com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ServiceRole {
    private uk.gov.pay.adminusers.model.Service service;

    private uk.gov.pay.adminusers.model.Role role;

    public static uk.gov.pay.adminusers.model.ServiceRole from(uk.gov.pay.adminusers.model.Service service, uk.gov.pay.adminusers.model.Role role) {
        return new uk.gov.pay.adminusers.model.ServiceRole(service, role);
    }

    private ServiceRole(@com.fasterxml.jackson.annotation.JsonProperty("service")
    uk.gov.pay.adminusers.model.Service service, @com.fasterxml.jackson.annotation.JsonProperty("role")
    uk.gov.pay.adminusers.model.Role role) {
        this.service = service;
        this.role = role;
    }

    public uk.gov.pay.adminusers.model.Service getService() {
        return service;
    }

    public uk.gov.pay.adminusers.model.Role getRole() {
        return role;
    }

    @java.lang.Override
    public java.lang.String toString() {
        return (((("ServiceRole{" + "Service=") + service) + ", role=") + role) + '}';
    }
}
