package uk.gov.pay.adminusers.model;
@com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public class Invite {
    private java.lang.String code;

    private final java.lang.String email;

    private final java.lang.String role;

    private java.lang.String telephoneNumber;

    private java.lang.Boolean disabled = java.lang.Boolean.FALSE;

    private java.lang.Integer attemptCounter = 0;

    private java.util.List<uk.gov.pay.adminusers.model.Link> links = new java.util.ArrayList<>();

    private java.lang.String type;

    private boolean userExist = false;

    private boolean expired;

    private boolean passwordSet;

    public Invite(java.lang.String code, java.lang.String email, java.lang.String telephoneNumber, java.lang.Boolean disabled, java.lang.Integer attemptCounter, java.lang.String type, java.lang.String role, java.lang.Boolean expired, boolean passwordSet) {
        this.code = code;
        this.email = email;
        this.telephoneNumber = telephoneNumber;
        this.disabled = disabled;
        this.attemptCounter = attemptCounter;
        this.type = type;
        this.role = role;
        this.expired = expired;
        this.passwordSet = passwordSet;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("email")
    @io.swagger.v3.oas.annotations.media.Schema(example = "example@example.gov.uk")
    public java.lang.String getEmail() {
        return email;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("telephone_number")
    @io.swagger.v3.oas.annotations.media.Schema(example = "+440787654534")
    public java.lang.String getTelephoneNumber() {
        return telephoneNumber;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("disabled")
    @io.swagger.v3.oas.annotations.media.Schema(example = "false")
    public java.lang.Boolean isDisabled() {
        return disabled;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("attempt_counter")
    @io.swagger.v3.oas.annotations.media.Schema(example = "0")
    public java.lang.Integer getAttemptCounter() {
        return attemptCounter;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("_links")
    public java.util.List<uk.gov.pay.adminusers.model.Link> getLinks() {
        return links;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("role")
    @io.swagger.v3.oas.annotations.media.Schema(example = "view-only")
    public java.lang.String getRole() {
        return role;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("expired")
    @io.swagger.v3.oas.annotations.media.Schema(example = "false")
    public java.lang.Boolean isExpired() {
        return expired;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("password_set")
    @io.swagger.v3.oas.annotations.media.Schema(example = "false")
    public boolean isPasswordSet() {
        return passwordSet;
    }

    public void setInviteLink(java.lang.String targetUrl) {
        uk.gov.pay.adminusers.model.Link inviteLink = uk.gov.pay.adminusers.model.Link.from(uk.gov.pay.adminusers.model.Link.Rel.INVITE, "GET", targetUrl);
        this.links.add(inviteLink);
    }

    @io.swagger.v3.oas.annotations.media.Schema(example = "service")
    public java.lang.String getType() {
        return type;
    }

    public void setType(java.lang.String type) {
        this.type = type;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public java.lang.String getCode() {
        return code;
    }

    public void setCode(java.lang.String code) {
        this.code = code;
    }

    /**
     * Derived attribute only to indicate if a user with the specified email already exits in the system.
     * This is not stored in database rather populated at runtime every time at the usage.
     *
     * @param userExist
     */
    public void setUserExist(boolean userExist) {
        this.userExist = userExist;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("user_exist")
    public boolean isUserExist() {
        return userExist;
    }
}
