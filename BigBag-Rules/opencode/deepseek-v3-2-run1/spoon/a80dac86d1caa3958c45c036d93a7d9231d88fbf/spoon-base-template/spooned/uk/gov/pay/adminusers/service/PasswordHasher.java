package uk.gov.pay.adminusers.service;
public class PasswordHasher {
    private static final int HASH_PASSWORD_SALT_ROUNDS = 10;

    public java.lang.String hash(java.lang.String password) {
        return org.mindrot.jbcrypt.BCrypt.hashpw(password, org.mindrot.jbcrypt.BCrypt.gensalt(uk.gov.pay.adminusers.service.PasswordHasher.HASH_PASSWORD_SALT_ROUNDS));
    }

    public boolean isEqual(java.lang.String password, java.lang.String hashedPassword) {
        return org.mindrot.jbcrypt.BCrypt.checkpw(password, hashedPassword);
    }
}
