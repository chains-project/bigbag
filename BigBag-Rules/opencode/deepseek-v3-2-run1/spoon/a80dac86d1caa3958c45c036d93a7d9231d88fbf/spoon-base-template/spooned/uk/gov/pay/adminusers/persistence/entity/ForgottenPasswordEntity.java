package uk.gov.pay.adminusers.persistence.entity;
@javax.persistence.Entity
@javax.persistence.Table(name = "forgotten_passwords")
@javax.persistence.SequenceGenerator(name = "forgotten_passwords_id_seq", sequenceName = "forgotten_passwords_id_seq", allocationSize = 1)
public class ForgottenPasswordEntity extends uk.gov.pay.adminusers.persistence.entity.AbstractEntity {
    @javax.persistence.Column(name = "date")
    @javax.persistence.Convert(converter = uk.gov.pay.adminusers.persistence.entity.UTCDateTimeConverter.class)
    private java.time.ZonedDateTime date;

    @javax.persistence.Column(name = "code")
    private java.lang.String code;

    /**
     * stupid sequalize created a column with with uppercase "I", hence the double quotes
     * TODO: rename this once we are completely migrated out of sequalize
     */
    @javax.persistence.ManyToOne
    @javax.persistence.JoinColumn(name = "\"userId\"", updatable = false)
    private uk.gov.pay.adminusers.persistence.entity.UserEntity user;

    // TODO: Change column from 'camelCase' to 'snake_case'. These columns were created through Sequelize.
    @javax.persistence.Column(name = "\"createdAt\"")
    @javax.persistence.Convert(converter = uk.gov.pay.adminusers.persistence.entity.UTCDateTimeConverter.class)
    private java.time.ZonedDateTime createdAt;

    @javax.persistence.Column(name = "\"updatedAt\"")
    @javax.persistence.Convert(converter = uk.gov.pay.adminusers.persistence.entity.UTCDateTimeConverter.class)
    private java.time.ZonedDateTime updatedAt;

    /**
     * For JPA
     */
    public ForgottenPasswordEntity() {
        super();
    }

    public ForgottenPasswordEntity(java.lang.String code, java.time.ZonedDateTime date, uk.gov.pay.adminusers.persistence.entity.UserEntity user) {
        super();
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC"));
        this.date = (date == null) ? now : date;
        this.code = code;
        this.user = user;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public java.time.ZonedDateTime getDate() {
        return date;
    }

    public void setDate(java.time.ZonedDateTime date) {
        this.date = date;
    }

    public java.lang.String getCode() {
        return code;
    }

    public void setCode(java.lang.String code) {
        this.code = code;
    }

    public uk.gov.pay.adminusers.persistence.entity.UserEntity getUser() {
        return user;
    }

    public void setUser(uk.gov.pay.adminusers.persistence.entity.UserEntity user) {
        this.user = user;
    }

    public java.time.ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.time.ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public java.time.ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(java.time.ZonedDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public static uk.gov.pay.adminusers.persistence.entity.ForgottenPasswordEntity from(uk.gov.pay.adminusers.model.ForgottenPassword forgottenPassword, uk.gov.pay.adminusers.persistence.entity.UserEntity user) {
        return new uk.gov.pay.adminusers.persistence.entity.ForgottenPasswordEntity(forgottenPassword.getCode(), forgottenPassword.getDate(), user);
    }

    public uk.gov.pay.adminusers.model.ForgottenPassword toForgottenPassword() {
        return uk.gov.pay.adminusers.model.ForgottenPassword.forgottenPassword(getId(), code, date, user.getExternalId());
    }
}
