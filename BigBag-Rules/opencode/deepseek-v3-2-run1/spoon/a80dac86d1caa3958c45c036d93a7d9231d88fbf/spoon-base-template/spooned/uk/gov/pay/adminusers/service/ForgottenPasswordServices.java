package uk.gov.pay.adminusers.service;
public class ForgottenPasswordServices {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(uk.gov.pay.adminusers.service.ForgottenPasswordServices.class);

    private static final java.lang.String SELFSERVICE_FORGOTTEN_PASSWORD_PATH = "reset-password";

    private final uk.gov.pay.adminusers.persistence.dao.UserDao userDao;

    private final uk.gov.pay.adminusers.persistence.dao.ForgottenPasswordDao forgottenPasswordDao;

    private final uk.gov.pay.adminusers.service.LinksBuilder linksBuilder;

    private final uk.gov.pay.adminusers.service.NotificationService notificationService;

    private final java.lang.String selfserviceBaseUrl;

    @com.google.inject.Inject
    public ForgottenPasswordServices(uk.gov.pay.adminusers.persistence.dao.UserDao userDao, uk.gov.pay.adminusers.persistence.dao.ForgottenPasswordDao forgottenPasswordDao, uk.gov.pay.adminusers.service.LinksBuilder linksBuilder, uk.gov.pay.adminusers.service.NotificationService notificationService, uk.gov.pay.adminusers.app.config.AdminUsersConfig config) {
        this.userDao = userDao;
        this.forgottenPasswordDao = forgottenPasswordDao;
        this.linksBuilder = linksBuilder;
        this.notificationService = notificationService;
        this.selfserviceBaseUrl = config.getLinks().getSelfserviceUrl();
    }

    public void create(java.lang.String username) {
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.UserEntity> userOptional = userDao.findByUsername(username);
        if (userOptional.isPresent()) {
            uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = userOptional.get();
            uk.gov.pay.adminusers.persistence.entity.ForgottenPasswordEntity forgottenPasswordEntity = new uk.gov.pay.adminusers.persistence.entity.ForgottenPasswordEntity(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid(), java.time.ZonedDateTime.now(), userEntity);
            forgottenPasswordDao.persist(forgottenPasswordEntity);
            java.lang.String forgottenPasswordUrl = javax.ws.rs.core.UriBuilder.fromUri(selfserviceBaseUrl).path(uk.gov.pay.adminusers.service.ForgottenPasswordServices.SELFSERVICE_FORGOTTEN_PASSWORD_PATH).path(forgottenPasswordEntity.getCode()).build().toString();
            try {
                java.lang.String notificationId = notificationService.sendForgottenPasswordEmail(userEntity.getEmail(), forgottenPasswordUrl);
                uk.gov.pay.adminusers.service.ForgottenPasswordServices.LOGGER.info("sent forgot password email successfully user [{}], notification id [{}]", userEntity.getExternalId(), notificationId);
            } catch (java.lang.Exception e) {
                uk.gov.pay.adminusers.service.ForgottenPasswordServices.LOGGER.error(java.lang.String.format("error sending forgotten password email for user [%s]", userEntity.getExternalId()), e);
            }
        } else {
            uk.gov.pay.adminusers.service.ForgottenPasswordServices.LOGGER.warn("Attempted forgotten password for non existent user {}", username);
            throw uk.gov.pay.adminusers.service.AdminUsersExceptions.notFoundException();
        }
    }

    public java.util.Optional<uk.gov.pay.adminusers.model.ForgottenPassword> findNonExpired(java.lang.String code) {
        return forgottenPasswordDao.findNonExpiredByCode(code).map(forgottenPasswordEntity -> java.util.Optional.of(linksBuilder.decorate(forgottenPasswordEntity.toForgottenPassword()))).orElseGet(() -> {
            uk.gov.pay.adminusers.service.ForgottenPasswordServices.LOGGER.warn("Attempted forgotten password GET for non-existent/expired code {}", code);
            return java.util.Optional.empty();
        });
    }
}
