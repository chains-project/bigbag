package uk.gov.pay.adminusers.service;
public interface ServiceServicesFactory {
    uk.gov.pay.adminusers.service.ServiceCreator serviceCreator();

    uk.gov.pay.adminusers.service.ServiceUpdater serviceUpdater();

    uk.gov.pay.adminusers.service.ServiceUserRemover serviceUserRemover();

    uk.gov.pay.adminusers.service.ServiceFinder serviceFinder();
}
