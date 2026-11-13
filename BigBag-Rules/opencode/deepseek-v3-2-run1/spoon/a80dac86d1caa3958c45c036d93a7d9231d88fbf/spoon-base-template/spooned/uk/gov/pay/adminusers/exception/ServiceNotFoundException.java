package uk.gov.pay.adminusers.exception;
public class ServiceNotFoundException extends uk.gov.pay.adminusers.exception.NotFoundException {
    public ServiceNotFoundException(java.lang.String serviceExternalId) {
        super(("Service with serviceExternalId = \"" + serviceExternalId) + "\" NOT FOUND");
    }
}
