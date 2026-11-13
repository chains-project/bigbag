package uk.gov.pay.adminusers.model;
@com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
public class User {
    public static final java.lang.String FIELD_USERNAME = "username";

    public static final java.lang.String FIELD_SERVICE_EXTERNAL_ID = "service_external_id";

    public static final java.lang.String FIELD_PASSWORD = "password";

    public static final java.lang.String FIELD_EMAIL = "email";

    public static final java.lang.String FIELD_TELEPHONE_NUMBER = "telephone_number";

    public static final java.lang.String FIELD_ROLE_NAME = "role_name";

    private java.lang.Integer id;

    @io.swagger.v3.oas.annotations.media.Schema(example = "93ba1ec4ed6a4238a59f16ad97b4fa12")
    private java.lang.String externalId;

    @io.swagger.v3.oas.annotations.media.Schema(example = "user@somegovernmentdept.gov.uk")
    private java.lang.String username;

    private java.lang.String password;

    @io.swagger.v3.oas.annotations.media.Schema(example = "user@somegovernmentdept.gov.uk")
    private java.lang.String email;

    @io.swagger.v3.oas.annotations.media.Schema(example = "447700900000")
    private java.lang.String telephoneNumber;

    @io.swagger.v3.oas.annotations.media.Schema(example = "43c3c4t")
    private java.lang.String otpKey;

    @io.swagger.v3.oas.annotations.media.Schema(example = "false")
    private java.lang.Boolean disabled = java.lang.Boolean.FALSE;

    @io.swagger.v3.oas.annotations.media.Schema(example = "0")
    private java.lang.Integer loginCounter = 0;

    @io.swagger.v3.oas.annotations.media.Schema(example = "feature1, feature2")
    private java.lang.String features;

    private java.util.List<uk.gov.pay.adminusers.model.ServiceRole> serviceRoles;

    @io.swagger.v3.oas.annotations.media.Schema(example = "SMS")
    private uk.gov.pay.adminusers.model.SecondFactorMethod secondFactor;

    @io.swagger.v3.oas.annotations.media.Schema(example = "FC5IHFH2CFSKEZBBXTYEGQXQOH344LLO")
    private java.lang.String provisionalOtpKey;

    @io.swagger.v3.oas.annotations.media.Schema(example = "2022-04-8T12:09:43.698Z")
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = uk.gov.service.payments.commons.api.json.ApiResponseDateTimeSerializer.class)
    private java.time.ZonedDateTime provisionalOtpKeyCreatedAt;

    @io.swagger.v3.oas.annotations.media.Schema(example = "2022-04-06T23:03:41.665Z")
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = uk.gov.service.payments.commons.api.json.ApiResponseDateTimeSerializer.class)
    private java.time.ZonedDateTime lastLoggedInAt;

    private java.util.List<uk.gov.pay.adminusers.model.Link> links = new java.util.ArrayList<>();

    private java.lang.Integer sessionVersion = 0;

    public static uk.gov.pay.adminusers.model.User from(java.lang.Integer id, java.lang.String externalId, java.lang.String username, java.lang.String password, java.lang.String email, java.lang.String otpKey, java.lang.String telephoneNumber, java.util.List<uk.gov.pay.adminusers.model.ServiceRole> serviceRoles, java.lang.String features, uk.gov.pay.adminusers.model.SecondFactorMethod secondFactor, java.lang.String provisionalOtpKey, java.time.ZonedDateTime provisionalOtpKeyCreatedAt, java.time.ZonedDateTime lastLoggedInAt) {
        return new uk.gov.pay.adminusers.model.User(id, externalId, username, password, email, otpKey, telephoneNumber, serviceRoles, features, secondFactor, provisionalOtpKey, provisionalOtpKeyCreatedAt, lastLoggedInAt);
    }

    private User(java.lang.Integer id, @com.fasterxml.jackson.annotation.JsonProperty("external_id")
    java.lang.String externalId, @com.fasterxml.jackson.annotation.JsonProperty("username")
    java.lang.String username, @com.fasterxml.jackson.annotation.JsonProperty("password")
    java.lang.String password, @com.fasterxml.jackson.annotation.JsonProperty("email")
    java.lang.String email, @com.fasterxml.jackson.annotation.JsonProperty("otp_key")
    java.lang.String otpKey, @com.fasterxml.jackson.annotation.JsonProperty("telephone_number")
    java.lang.String telephoneNumber, @com.fasterxml.jackson.annotation.JsonProperty("service_roles")
    java.util.List<uk.gov.pay.adminusers.model.ServiceRole> serviceRoles, @com.fasterxml.jackson.annotation.JsonProperty("features")
    java.lang.String features, @com.fasterxml.jackson.annotation.JsonProperty("second_factor")
    uk.gov.pay.adminusers.model.SecondFactorMethod secondFactor, @com.fasterxml.jackson.annotation.JsonProperty("provisional_otp_key")
    java.lang.String provisionalOtpKey, @com.fasterxml.jackson.annotation.JsonProperty("provisional_otp_key_created_at")
    java.time.ZonedDateTime provisionalOtpKeyCreatedAt, @com.fasterxml.jackson.annotation.JsonProperty("last_logged_in_at")
    java.time.ZonedDateTime lastLoggedInAt) {
        this.id = id;
        this.externalId = externalId;
        this.username = username;
        this.password = password;
        this.email = email;
        this.otpKey = otpKey;
        this.telephoneNumber = telephoneNumber;
        this.serviceRoles = serviceRoles;
        this.features = features;
        this.secondFactor = secondFactor;
        this.provisionalOtpKey = provisionalOtpKey;
        this.provisionalOtpKeyCreatedAt = provisionalOtpKeyCreatedAt;
        this.lastLoggedInAt = lastLoggedInAt;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public java.lang.Integer getId() {
        return id;
    }

    public java.lang.String getExternalId() {
        return externalId;
    }

    public java.lang.String getUsername() {
        return username;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public java.lang.String getPassword() {
        return password;
    }

    public java.lang.String getEmail() {
        return email;
    }

    public java.lang.String getOtpKey() {
        return otpKey;
    }

    public java.lang.String getTelephoneNumber() {
        return telephoneNumber;
    }

    @com.fasterxml.jackson.annotation.JsonGetter
    public java.lang.Boolean isDisabled() {
        return disabled;
    }

    public java.lang.Integer getLoginCounter() {
        return loginCounter;
    }

    public void setDisabled(java.lang.Boolean disabled) {
        this.disabled = disabled;
    }

    public java.lang.String getFeatures() {
        return features;
    }

    public void setFeatures(java.lang.String features) {
        this.features = features;
    }

    public void setLoginCounter(java.lang.Integer loginCounter) {
        this.loginCounter = loginCounter;
    }

    public void setSessionVersion(java.lang.Integer sessionVersion) {
        this.sessionVersion = sessionVersion;
    }

    public java.lang.Integer getSessionVersion() {
        return sessionVersion;
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

    @com.fasterxml.jackson.annotation.JsonProperty("_links")
    public java.util.List<uk.gov.pay.adminusers.model.Link> getLinks() {
        return links;
    }

    public void setLinks(java.util.List<uk.gov.pay.adminusers.model.Link> links) {
        this.links = links;
    }

    /**
     * it’s definitely not a good idea to toString() password / otpKey
     *
     * @return  */
    @java.lang.Override
    public java.lang.String toString() {
        return (((((((("User{" + "externalId=") + externalId) + ", secondFactor=") + secondFactor) + ", disabled=") + disabled) + ", serviceRoles=") + serviceRoles) + '}';
    }

    public java.util.List<uk.gov.pay.adminusers.model.ServiceRole> getServiceRoles() {
        return serviceRoles;
    }
}
