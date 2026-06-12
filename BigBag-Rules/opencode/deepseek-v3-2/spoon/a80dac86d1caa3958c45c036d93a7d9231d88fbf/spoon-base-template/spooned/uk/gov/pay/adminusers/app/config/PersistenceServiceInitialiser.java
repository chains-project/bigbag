package uk.gov.pay.adminusers.app.config;
public class PersistenceServiceInitialiser {
    @javax.inject.Inject
    public PersistenceServiceInitialiser(com.google.inject.persist.PersistService service) {
        service.start();
    }
}
