package uk.gov.pay.adminusers.service;
public class ResetPasswordService {
    private final uk.gov.pay.adminusers.persistence.dao.UserDao userDao;

    private final uk.gov.pay.adminusers.persistence.dao.ForgottenPasswordDao forgottenPasswordDao;

    private final uk.gov.pay.adminusers.service.PasswordHasher passwordHasher;

    @com.google.inject.Inject
    public ResetPasswordService(uk.gov.pay.adminusers.persistence.dao.UserDao userDao, uk.gov.pay.adminusers.persistence.dao.ForgottenPasswordDao forgottenPasswordDao, uk.gov.pay.adminusers.service.PasswordHasher passwordHasher) {
        this.userDao = userDao;
        this.forgottenPasswordDao = forgottenPasswordDao;
        this.passwordHasher = passwordHasher;
    }

    @com.google.inject.persist.Transactional
    public java.util.Optional<java.lang.Integer> updatePassword(java.lang.String code, java.lang.String password) {
        return forgottenPasswordDao.findNonExpiredByCode(code).map(forgottenPassword -> {
            uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = forgottenPassword.getUser();
            userEntity.setLoginCounter(0);
            userEntity.setPassword(passwordHasher.hash(password));
            userDao.merge(userEntity);
            forgottenPasswordDao.remove(forgottenPassword);
            return java.util.Optional.of(userEntity.getId());
        }).orElseGet(java.util.Optional::empty);
    }
}
