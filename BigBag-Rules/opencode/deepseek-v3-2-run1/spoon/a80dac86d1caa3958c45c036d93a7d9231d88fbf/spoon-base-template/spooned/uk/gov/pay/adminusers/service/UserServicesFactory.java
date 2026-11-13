package uk.gov.pay.adminusers.service;
public interface UserServicesFactory {
    uk.gov.pay.adminusers.service.ServiceRoleUpdater serviceRoleUpdater();

    uk.gov.pay.adminusers.service.ServiceRoleCreator serviceRoleCreator();

    uk.gov.pay.adminusers.service.UserCreator userCreator();
}
