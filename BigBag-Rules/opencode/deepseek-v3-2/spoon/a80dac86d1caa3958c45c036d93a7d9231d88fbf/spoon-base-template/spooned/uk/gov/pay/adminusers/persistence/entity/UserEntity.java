package uk.gov.pay.adminusers.persistence.entity;
@javax.persistence.Entity
@javax.persistence.Table(name = "users")
@javax.persistence.SequenceGenerator(name = "users_id_seq", sequenceName = "users_id_seq", allocationSize = 1)
public class UserEntity extends uk.gov.pay.adminusers.persistence.entity.AbstractEntity {
    @javax.persistence.Column(name = "external_id")
    private java.lang.String externalId;

    @javax.persistence.Column(name = "username")
    private java.lang.String username;

    @javax.persistence.Column(name = "password")
    private java.lang.String password;

    @javax.persistence.Column(name = "email")
    private java.lang.String email;

    @javax.persistence.Column(name = "otp_key")
    private java.lang.String otpKey;

    @javax.persistence.Column(name = "telephone_number")
    private java.lang.String telephoneNumber;

    @javax.persistence.Column(name = "disabled")
    private java.lang.Boolean disabled = java.lang.Boolean.FALSE;

    @javax.persistence.Column(name = "login_counter")
    private java.lang.Integer loginCounter = 0;

    @javax.persistence.Column(name = "features")
    private java.lang.String features;

    @javax.persistence.OneToMany(mappedBy = "user", cascade = javax.persistence.CascadeType.PERSIST, targetEntity = uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity.class)
    private java.util.List<uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity> servicesRoles = new java.util.ArrayList<>();

    // TODO: Change column from 'camelCase' to 'snake_case'. These columns were created through Sequelize.
    @javax.persistence.Column(name = "\"createdAt\"")
    @javax.persistence.Convert(converter = uk.gov.pay.adminusers.persistence.entity.UTCDateTimeConverter.class)
    private java.time.ZonedDateTime createdAt;

    @javax.persistence.Column(name = "\"updatedAt\"")
    @javax.persistence.Convert(converter = uk.gov.pay.adminusers.persistence.entity.UTCDateTimeConverter.class)
    private java.time.ZonedDateTime updatedAt;

    @javax.persistence.Column(name = "session_version", columnDefinition = "int default 0")
    private java.lang.Integer sessionVersion;

    @javax.persistence.Column(name = "second_factor", nullable = false)
    @javax.persistence.Convert(converter = uk.gov.pay.adminusers.persistence.entity.SecondFactorMethodConverter.class)
    private uk.gov.pay.adminusers.model.SecondFactorMethod secondFactor;

    @javax.persistence.Column(name = "provisional_otp_key")
    private java.lang.String provisionalOtpKey;

    @javax.persistence.Column(name = "provisional_otp_key_created_at")
    @javax.persistence.Convert(converter = uk.gov.pay.adminusers.persistence.entity.UTCDateTimeConverter.class)
    private java.time.ZonedDateTime provisionalOtpKeyCreatedAt;

    @javax.persistence.Column(name = "last_logged_in_at")
    @javax.persistence.Convert(converter = uk.gov.pay.adminusers.persistence.entity.UTCDateTimeConverter.class)
    private java.time.ZonedDateTime lastLoggedInAt;

    /**
     * For JPA
     */
    public UserEntity() {
        super();
    }

    public java.lang.String getExternalId() {
        return externalId;
    }

    public void setExternalId(java.lang.String externalId) {
        this.externalId = externalId;
    }

    public java.lang.String getUsername() {
        return username;
    }

    public void setUsername(java.lang.String username) {
        this.username = username;
    }

    public java.lang.String getPassword() {
        return password;
    }

    public void setPassword(java.lang.String password) {
        this.password = password;
    }

    public java.lang.String getEmail() {
        return email;
    }

    public void setEmail(java.lang.String email) {
        this.email = email;
    }

    public java.lang.String getGatewayAccountId() {
        return this.servicesRoles.get(0).getService().getGatewayAccountId().getGatewayAccountId();
    }

    public java.lang.String getOtpKey() {
        return otpKey;
    }

    public void setOtpKey(java.lang.String otpKey) {
        this.otpKey = otpKey;
    }

    public java.lang.String getTelephoneNumber() {
        return telephoneNumber;
    }

    public void setTelephoneNumber(java.lang.String telephoneNumber) {
        this.telephoneNumber = telephoneNumber;
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

    public java.lang.String getFeatures() {
        return features;
    }

    public void setFeatures(java.lang.String features) {
        this.features = features;
    }

    public java.util.List<uk.gov.pay.adminusers.persistence.entity.RoleEntity> getRoles() {
        return servicesRoles.isEmpty() ? java.util.Collections.emptyList() : java.util.Collections.singletonList(servicesRoles.get(0).getRole());
    }

    public java.time.ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(java.time.ZonedDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public java.time.ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.time.ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public java.lang.Integer getSessionVersion() {
        return sessionVersion;
    }

    public void setSessionVersion(java.lang.Integer sessionVersion) {
        this.sessionVersion = sessionVersion;
    }

    public uk.gov.pay.adminusers.model.SecondFactorMethod getSecondFactor() {
        return secondFactor;
    }

    public void setSecondFactor(uk.gov.pay.adminusers.model.SecondFactorMethod secondFactor) {
        this.secondFactor = secondFactor;
    }

    public java.lang.String getProvisionalOtpKey() {
        return provisionalOtpKey;
    }

    public void setProvisionalOtpKey(java.lang.String provisionalOtpKey) {
        this.provisionalOtpKey = provisionalOtpKey;
    }

    public java.time.ZonedDateTime getProvisionalOtpKeyCreatedAt() {
        return provisionalOtpKeyCreatedAt;
    }

    public void setProvisionalOtpKeyCreatedAt(java.time.ZonedDateTime provisionalOtpKeyCreatedAt) {
        this.provisionalOtpKeyCreatedAt = provisionalOtpKeyCreatedAt;
    }

    public java.time.ZonedDateTime getLastLoggedInAt() {
        return lastLoggedInAt;
    }

    public void setLastLoggedInAt(java.time.ZonedDateTime lastLoggedInAt) {
        this.lastLoggedInAt = lastLoggedInAt;
    }

    /**
     * Note: this constructor will not copy <b>id</b> from the User model. It will always assign a new one internally (by JPA)
     *
     * @param user
     * @return persistable UserEntity object not bounded to entity manager
     */
    public static uk.gov.pay.adminusers.persistence.entity.UserEntity from(uk.gov.pay.adminusers.model.User user) {
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = new uk.gov.pay.adminusers.persistence.entity.UserEntity();
        userEntity.setExternalId(user.getExternalId());
        userEntity.setUsername(user.getUsername());
        userEntity.setPassword(user.getPassword());
        userEntity.setEmail(user.getEmail());
        userEntity.setOtpKey(user.getOtpKey());
        userEntity.setTelephoneNumber(uk.gov.pay.adminusers.utils.telephonenumber.TelephoneNumberUtility.formatToE164(user.getTelephoneNumber()));
        userEntity.setSecondFactor(user.getSecondFactor());
        userEntity.setProvisionalOtpKey(user.getProvisionalOtpKey());
        userEntity.setProvisionalOtpKeyCreatedAt(user.getProvisionalOtpKeyCreatedAt());
        userEntity.setLoginCounter(user.getLoginCounter());
        userEntity.setFeatures(user.getFeatures());
        userEntity.setDisabled(user.isDisabled());
        userEntity.setSessionVersion(user.getSessionVersion());
        java.time.ZonedDateTime timeNow = java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC"));
        userEntity.setCreatedAt(timeNow);
        userEntity.setUpdatedAt(timeNow);
        return userEntity;
    }

    /**
     * Creates UserEntity object from CreateUserRequest object
     *
     * @param createUserRequest
     * @return persistable UserEntity object not bounded to entity manager
     */
    public static uk.gov.pay.adminusers.persistence.entity.UserEntity from(uk.gov.pay.adminusers.model.CreateUserRequest createUserRequest) {
        uk.gov.pay.adminusers.persistence.entity.UserEntity userEntity = new uk.gov.pay.adminusers.persistence.entity.UserEntity();
        userEntity.setExternalId(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid());
        userEntity.setUsername(createUserRequest.getUsername());
        userEntity.setPassword(createUserRequest.getPassword());
        userEntity.setEmail(createUserRequest.getEmail());
        userEntity.setOtpKey(createUserRequest.getOtpKey());
        userEntity.setTelephoneNumber(uk.gov.pay.adminusers.utils.telephonenumber.TelephoneNumberUtility.formatToE164(createUserRequest.getTelephoneNumber()));
        userEntity.setSecondFactor(uk.gov.pay.adminusers.model.SecondFactorMethod.SMS);
        userEntity.setLoginCounter(0);
        userEntity.setFeatures(createUserRequest.getFeatures());
        userEntity.setDisabled(java.lang.Boolean.FALSE);
        userEntity.setSessionVersion(0);
        java.time.ZonedDateTime timeNow = java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC"));
        userEntity.setCreatedAt(timeNow);
        userEntity.setUpdatedAt(timeNow);
        return userEntity;
    }

    public uk.gov.pay.adminusers.model.User toUser() {
        java.util.List<uk.gov.pay.adminusers.model.ServiceRole> serviceRoles = this.servicesRoles.stream().map(uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity::toServiceRole).collect(java.util.stream.Collectors.toUnmodifiableList());
        uk.gov.pay.adminusers.model.User user = uk.gov.pay.adminusers.model.User.from(getId(), externalId, username, password, email, otpKey, telephoneNumber, serviceRoles, features, secondFactor, provisionalOtpKey, provisionalOtpKeyCreatedAt, lastLoggedInAt);
        user.setLoginCounter(loginCounter);
        user.setDisabled(disabled);
        user.setSessionVersion(sessionVersion);
        return user;
    }

    public void addServiceRole(uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity serviceRole) {
        serviceRole.setUser(this);
        this.servicesRoles.add(serviceRole);
    }

    // Use external Id version
    @java.lang.Deprecated
    public java.util.Optional<uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity> getServicesRole(java.lang.Integer serviceId) {
        return servicesRoles.stream().filter(serviceRoleEntity -> serviceId.equals(serviceRoleEntity.getService().getId())).findFirst();
    }

    public java.util.Optional<uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity> getServicesRole(java.lang.String serviceExternalId) {
        return servicesRoles.stream().filter(serviceRoleEntity -> serviceExternalId.equals(serviceRoleEntity.getService().getExternalId())).findFirst();
    }

    public boolean canInviteUsersTo(java.lang.Integer serviceId) {
        java.util.Optional<uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity> serviceRole = this.getServicesRole(serviceId);
        return (serviceRole.isPresent() && serviceRole.get().getRole().isAdmin()) && serviceRole.get().getService().getId().equals(serviceId);
    }

    public void remove(uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity serviceRole) {
        servicesRoles.remove(serviceRole);
    }

    public java.util.List<uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity> getServicesRoles() {
        return servicesRoles;
    }
}
