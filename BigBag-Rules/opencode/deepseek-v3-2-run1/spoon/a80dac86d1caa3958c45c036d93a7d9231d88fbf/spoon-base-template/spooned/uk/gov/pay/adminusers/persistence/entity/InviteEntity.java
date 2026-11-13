package uk.gov.pay.adminusers.persistence.entity;
@javax.persistence.Entity
@javax.persistence.Table(name = "invites")
public class InviteEntity extends uk.gov.pay.adminusers.persistence.entity.AbstractEntity {
    private static final long EXPIRY_DAYS = 2L;

    @javax.persistence.Column(name = "date")
    @javax.persistence.Convert(converter = uk.gov.pay.adminusers.persistence.entity.UTCDateTimeConverter.class)
    private java.time.ZonedDateTime date;

    @javax.persistence.Column(name = "expiry_date")
    @javax.persistence.Convert(converter = uk.gov.pay.adminusers.persistence.entity.UTCDateTimeConverter.class)
    private java.time.ZonedDateTime expiryDate;

    @javax.persistence.ManyToOne
    @javax.persistence.JoinColumn(name = "role_id", nullable = false)
    private uk.gov.pay.adminusers.persistence.entity.RoleEntity role;

    @javax.persistence.ManyToOne
    @javax.persistence.JoinColumn(name = "service_id")
    private uk.gov.pay.adminusers.persistence.entity.ServiceEntity service;

    @javax.persistence.ManyToOne
    @javax.persistence.JoinColumn(name = "sender_id")
    private uk.gov.pay.adminusers.persistence.entity.UserEntity sender;

    @javax.persistence.Column(name = "email")
    private java.lang.String email;

    @javax.persistence.Column(name = "code")
    private java.lang.String code;

    @javax.persistence.Column(name = "otp_key")
    private java.lang.String otpKey;

    @javax.persistence.Column(name = "telephone_number")
    private java.lang.String telephoneNumber;

    @javax.persistence.Column(name = "password")
    private java.lang.String password;

    @javax.persistence.Column(name = "disabled")
    private java.lang.Boolean disabled = java.lang.Boolean.FALSE;

    @javax.persistence.Column(name = "login_counter")
    private java.lang.Integer loginCounter = 0;

    @javax.persistence.Column(name = "type")
    @javax.persistence.Convert(converter = uk.gov.pay.adminusers.persistence.entity.InviteTypeConverter.class)
    private uk.gov.pay.adminusers.model.InviteType type = uk.gov.pay.adminusers.model.InviteType.USER;

    /**
     * For JPA
     */
    public InviteEntity() {
        super();
    }

    public InviteEntity(java.lang.String email, java.lang.String code, java.lang.String otpKey, uk.gov.pay.adminusers.persistence.entity.RoleEntity role) {
        super();
        this.date = java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC"));
        initializeExpiry();
        this.code = code;
        this.otpKey = otpKey;
        this.email = email.toLowerCase(java.util.Locale.ENGLISH);
        this.role = role;
    }

    /**
     * Being:
     * <p>
     * 'X' the moment the invite is created and
     * '|' = 00:00 of the following day
     * '^' the moment it expires
     * 'N' = Now
     * <p>
     * <-------Day 0---------><-------Day 1---------><-------Day 2--------->
     * |----------------------|----------------------|----------------------|
     * X                               N             ^
     * <p>
     * Invite created Day 1 -> 00:00:00:000 will expired at Day 3 -> 00:00:00:000
     */
    private void initializeExpiry() {
        this.expiryDate = this.date.truncatedTo(java.time.temporal.ChronoUnit.DAYS).plus(uk.gov.pay.adminusers.persistence.entity.InviteEntity.EXPIRY_DAYS, java.time.temporal.ChronoUnit.DAYS);
    }

    public java.time.ZonedDateTime getDate() {
        return date;
    }

    public void setDate(java.time.ZonedDateTime date) {
        this.date = date;
    }

    public void setExpiryDate(java.time.ZonedDateTime expiryDate) {
        this.expiryDate = expiryDate;
    }

    public java.time.ZonedDateTime getExpiryDate() {
        return expiryDate;
    }

    public java.lang.String getCode() {
        return code;
    }

    public void setCode(java.lang.String code) {
        this.code = code;
    }

    public java.lang.String getOtpKey() {
        return otpKey;
    }

    public void setOtpKey(java.lang.String otpKey) {
        this.otpKey = otpKey;
    }

    public uk.gov.pay.adminusers.persistence.entity.RoleEntity getRole() {
        return role;
    }

    public void setRole(uk.gov.pay.adminusers.persistence.entity.RoleEntity role) {
        this.role = role;
    }

    public uk.gov.pay.adminusers.persistence.entity.ServiceEntity getService() {
        return service;
    }

    public void setService(uk.gov.pay.adminusers.persistence.entity.ServiceEntity service) {
        this.service = service;
    }

    public java.lang.String getEmail() {
        return email;
    }

    public void setEmail(java.lang.String email) {
        this.email = email;
    }

    public uk.gov.pay.adminusers.persistence.entity.UserEntity getSender() {
        return sender;
    }

    public void setSender(uk.gov.pay.adminusers.persistence.entity.UserEntity sender) {
        this.sender = sender;
    }

    public java.lang.String getTelephoneNumber() {
        return telephoneNumber;
    }

    public void setTelephoneNumber(java.lang.String telephoneNumber) {
        this.telephoneNumber = telephoneNumber;
    }

    public java.lang.String getPassword() {
        return password;
    }

    public void setPassword(java.lang.String password) {
        this.password = password;
    }

    public java.lang.Boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(java.lang.Boolean disabled) {
        this.disabled = disabled;
    }

    public java.lang.Integer getLoginCounter() {
        return loginCounter;
    }

    public void setLoginCounter(java.lang.Integer loginCount) {
        this.loginCounter = loginCount;
    }

    public uk.gov.pay.adminusers.model.InviteType getType() {
        return type;
    }

    public void setType(uk.gov.pay.adminusers.model.InviteType type) {
        this.type = type;
    }

    public boolean isServiceType() {
        return uk.gov.pay.adminusers.model.InviteType.SERVICE.equals(type);
    }

    public boolean isUserType() {
        return uk.gov.pay.adminusers.model.InviteType.USER.equals(type);
    }

    public uk.gov.pay.adminusers.model.Invite toInvite() {
        return new uk.gov.pay.adminusers.model.Invite(code, email, telephoneNumber, disabled, loginCounter, type.getType(), role.getName(), isExpired(), hasPassword());
    }

    public boolean isExpired() {
        return java.time.ZonedDateTime.now(uk.gov.pay.adminusers.persistence.entity.UTCDateTimeConverter.UTC).isAfter(expiryDate);
    }

    private boolean hasPassword() {
        return password != null;
    }

    public uk.gov.pay.adminusers.persistence.entity.UserEntity mapToUserEntity() {
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = new uk.gov.pay.adminusers.persistence.entity.UserEntity();
        userEntity.setExternalId(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid());
        userEntity.setUsername(email);
        userEntity.setPassword(password);
        userEntity.setEmail(email);
        userEntity.setOtpKey(otpKey);
        if (telephoneNumber != null) {
            userEntity.setTelephoneNumber(uk.gov.pay.adminusers.utils.telephonenumber.TelephoneNumberUtility.formatToE164(telephoneNumber));
        }
        userEntity.setSecondFactor(uk.gov.pay.adminusers.model.SecondFactorMethod.SMS);
        userEntity.setLoginCounter(0);
        userEntity.setDisabled(java.lang.Boolean.FALSE);
        userEntity.setSessionVersion(0);
        if (service != null) {
            userEntity.addServiceRole(new uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity(service, role));
        }
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC"));
        userEntity.setCreatedAt(now);
        userEntity.setUpdatedAt(now);
        return userEntity;
    }
}
