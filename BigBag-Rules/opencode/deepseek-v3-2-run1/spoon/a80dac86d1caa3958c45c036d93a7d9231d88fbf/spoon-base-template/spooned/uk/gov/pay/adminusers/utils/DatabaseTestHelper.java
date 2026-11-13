package uk.gov.pay.adminusers.utils;
public class DatabaseTestHelper {
    private org.jdbi.v3.core.Jdbi jdbi;

    public DatabaseTestHelper(org.jdbi.v3.core.Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public java.util.List<java.util.Map<java.lang.String, java.lang.Object>> findUserByExternalId(java.lang.String externalId) {
        return jdbi.withHandle(h -> h.createQuery(("SELECT id, external_id, username, password, email, otp_key, telephone_number, disabled, login_counter, \"createdAt\", \"updatedAt\", session_version " + "FROM users ") + "WHERE external_id = :externalId").bind("externalId", externalId).mapToMap().list());
    }

    public java.util.List<java.util.Map<java.lang.String, java.lang.Object>> findUserByUsername(java.lang.String username) {
        return jdbi.withHandle(h -> h.createQuery(("SELECT id, external_id, username, password, email, otp_key, telephone_number, disabled, login_counter, \"createdAt\", \"updatedAt\", session_version " + "FROM users ") + "WHERE username = :username").bind("username", username).mapToMap().list());
    }

    public java.util.List<java.util.Map<java.lang.String, java.lang.Object>> findUser(long userId) {
        return jdbi.withHandle(h -> h.createQuery(("SELECT id, external_id, username, password, email, otp_key, telephone_number, disabled, login_counter, \"createdAt\", \"updatedAt\", session_version " + "FROM users ") + "WHERE id = :userId").bind("userId", userId).mapToMap().list());
    }

    public java.util.List<java.util.Map<java.lang.String, java.lang.Object>> findServiceRoleForUser(long userId) {
        return jdbi.withHandle(h -> h.createQuery(("SELECT r.id, r.name, r.description, ur.service_id " + "FROM roles r INNER JOIN user_services_roles ur ") + "ON ur.user_id = :userId AND ur.role_id = r.id").bind("userId", userId).mapToMap().list());
    }

    public java.util.List<java.util.Map<java.lang.String, java.lang.Object>> findForgottenPasswordById(java.lang.Integer forgottenPasswordId) {
        return jdbi.withHandle(h -> h.createQuery(("SELECT id, date, code, \"userId\" " + "FROM forgotten_passwords ") + "WHERE id = :id").bind("id", forgottenPasswordId).mapToMap().list());
    }

    public java.util.List<java.util.Map<java.lang.String, java.lang.Object>> findInviteById(java.lang.Integer inviteId) {
        return jdbi.withHandle(h -> h.createQuery(("SELECT id, sender_id, date, code, email, role_id, service_id, otp_key, telephone_number, disabled, login_counter " + "FROM invites ") + "WHERE id = :id").bind("id", inviteId).mapToMap().list());
    }

    public uk.gov.pay.adminusers.utils.DatabaseTestHelper updateLoginCount(java.lang.String username, int loginCount) {
        jdbi.withHandle(handle -> handle.createUpdate("UPDATE users SET login_counter = :loginCount " + "WHERE username = :username").bind("loginCount", loginCount).bind("username", username).execute());
        return this;
    }

    public uk.gov.pay.adminusers.utils.DatabaseTestHelper updateProvisionalOtpKey(java.lang.String username, java.lang.String provisionalOtpKey) {
        jdbi.withHandle(handle -> handle.createUpdate(("UPDATE users SET provisional_otp_key = :provisionalOtpKey, " + "provisional_otp_key_created_at = NOW() ") + "WHERE username = :username").bind("provisionalOtpKey", provisionalOtpKey).bind("username", username).execute());
        return this;
    }

    public uk.gov.pay.adminusers.utils.DatabaseTestHelper add(uk.gov.pay.adminusers.model.User user) {
        java.sql.Timestamp now = java.sql.Timestamp.from(java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")).toInstant());
        jdbi.withHandle(handle -> handle.createUpdate((((("INSERT INTO users(" + "id, external_id, username, password, email, otp_key, telephone_number, ") + "second_factor, disabled, login_counter, version, ") + "\"createdAt\", \"updatedAt\", session_version, provisional_otp_key) ") + "VALUES (:id, :externalId, :username, :password, :email, :otpKey, :telephoneNumber, ") + ":secondFactor, :disabled, :loginCounter, :version, :createdAt, :updatedAt, :session_version, :provisionalOtpKey)").bind("id", user.getId()).bind("externalId", user.getExternalId()).bind("username", user.getUsername()).bind("password", user.getPassword()).bind("email", user.getEmail()).bind("otpKey", user.getOtpKey()).bind("telephoneNumber", user.getTelephoneNumber()).bind("secondFactor", user.getSecondFactor().toString()).bind("disabled", user.isDisabled()).bind("loginCounter", user.getLoginCounter()).bind("version", 0).bind("session_version", user.getSessionVersion()).bind("createdAt", now).bind("updatedAt", now).bind("provisionalOtpKey", user.getProvisionalOtpKey()).execute());
        return this;
    }

    // inserting if not exist, just to be safe for fixed value inserts like Admin role
    public uk.gov.pay.adminusers.utils.DatabaseTestHelper add(uk.gov.pay.adminusers.model.Role role) {
        jdbi.withHandle(handle -> handle.createUpdate((("INSERT INTO roles(id, name, description) " + "SELECT :id, :name, :description ") + "WHERE NOT EXISTS (SELECT id FROM roles WHERE id = :id) ") + "RETURNING id").bind("id", role.getId()).bind("name", role.getName()).bind("description", role.getDescription()).execute());
        role.getPermissions().forEach(permission -> jdbi.withHandle(handle -> handle.createUpdate("INSERT INTO role_permission(role_id, permission_id) VALUES (:roleId, :permissionId)").bind("roleId", role.getId()).bind("permissionId", permission.getId()).execute()));
        return this;
    }

    public uk.gov.pay.adminusers.utils.DatabaseTestHelper add(uk.gov.pay.adminusers.model.Permission permission) {
        jdbi.withHandle(handle -> handle.createUpdate("INSERT INTO permissions(id, name, description) " + "VALUES (:id, :name, :description)").bind("id", permission.getId()).bind("name", permission.getName()).bind("description", permission.getDescription()).execute());
        return this;
    }

    public uk.gov.pay.adminusers.utils.DatabaseTestHelper add(uk.gov.pay.adminusers.model.ForgottenPassword forgottenPassword, java.lang.Integer userId) {
        jdbi.withHandle(handle -> handle.createUpdate("INSERT INTO forgotten_passwords(id, date, code, \"userId\") " + "VALUES (:id, :date, :code, :userId)").bind("id", forgottenPassword.getId()).bind("date", java.sql.Timestamp.from(forgottenPassword.getDate().toInstant())).bind("code", forgottenPassword.getCode()).bind("userId", userId).execute());
        return this;
    }

    // TODO Remove - This is temporary - WIP PP-1483
    public java.util.List<java.util.Map<java.lang.String, java.lang.Object>> findUserServicesByUserId(java.lang.Integer userId) {
        return jdbi.withHandle(h -> h.createQuery("SELECT service_id FROM user_services_roles " + "WHERE user_id = :userId").bind("userId", userId).mapToMap().list());
    }

    public uk.gov.pay.adminusers.utils.DatabaseTestHelper addService(uk.gov.pay.adminusers.model.Service service, java.lang.String... gatewayAccountIds) {
        jdbi.withHandle(handle -> {
            org.postgresql.util.PGobject customBranding = (service.getCustomBranding() == null) ? null : new uk.gov.pay.adminusers.persistence.entity.CustomBrandingConverter().convertToDatabaseColumn(service.getCustomBranding());
            uk.gov.pay.adminusers.model.MerchantDetails merchantDetails = service.getMerchantDetails();
            if (merchantDetails == null) {
                merchantDetails = new uk.gov.pay.adminusers.model.MerchantDetails();
            }
            return handle.createUpdate((((("INSERT INTO services(" + "id, custom_branding, ") + "merchant_name, merchant_telephone_number, merchant_address_line1, merchant_address_line2, merchant_address_city, ") + "merchant_address_postcode, merchant_address_country, merchant_email, merchant_url, external_id, experimental_features_enabled) ") + "VALUES (:id, :customBranding, :merchantName, :merchantTelephoneNumber, :merchantAddressLine1, :merchantAddressLine2, ") + ":merchantAddressCity, :merchantAddressPostcode, :merchantAddressCountry, :merchantEmail, :merchantUrl, :externalId, :experimentalFeaturesEnabled)").bind("id", service.getId()).bindBySqlType("customBranding", customBranding, java.sql.Types.OTHER).bind("merchantName", merchantDetails.getName()).bind("merchantTelephoneNumber", merchantDetails.getTelephoneNumber()).bind("merchantAddressLine1", merchantDetails.getAddressLine1()).bind("merchantAddressLine2", merchantDetails.getAddressLine2()).bind("merchantAddressCity", merchantDetails.getAddressCity()).bind("merchantAddressPostcode", merchantDetails.getAddressPostcode()).bind("merchantAddressCountry", merchantDetails.getAddressCountry()).bind("merchantEmail", merchantDetails.getEmail()).bind("merchantUrl", merchantDetails.getUrl()).bind("externalId", service.getExternalId()).bind("experimentalFeaturesEnabled", service.isExperimentalFeaturesEnabled()).execute();
        });
        addServiceName(uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity.from(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH, service.getName()), service.getId());
        for (java.lang.String gatewayAccountId : gatewayAccountIds) {
            jdbi.withHandle(handle -> handle.createUpdate("INSERT INTO service_gateway_accounts(service_id, gateway_account_id) VALUES (:serviceId, :gatewayAccountId)").bind("serviceId", service.getId()).bind("gatewayAccountId", gatewayAccountId).execute());
        }
        return this;
    }

    public uk.gov.pay.adminusers.utils.DatabaseTestHelper addUserServiceRole(java.lang.Integer userId, java.lang.Integer serviceId, java.lang.Integer roleId) {
        jdbi.withHandle(handle -> handle.createUpdate("INSERT INTO user_services_roles(user_id, service_id, role_id) VALUES(:userId, :serviceId, :roleId)").bind("userId", userId).bind("serviceId", serviceId).bind("roleId", roleId).execute());
        return this;
    }

    public uk.gov.pay.adminusers.utils.DatabaseTestHelper addInvite(int id, int senderId, int serviceId, int roleId, java.lang.String email, java.lang.String code, java.lang.String otpKey, java.time.ZonedDateTime date, java.time.ZonedDateTime expiryDate, java.lang.String telephoneNumber, java.lang.String password, java.lang.Boolean disabled, java.lang.Integer loginCounter) {
        jdbi.withHandle(handle -> handle.createUpdate("INSERT INTO invites(id, sender_id, service_id, role_id, email, code, otp_key, date, expiry_date, telephone_number, password, disabled, login_counter) " + "VALUES (:id, :senderId, :serviceId, :roleId, :email, :code, :otpKey, :date, :expiryDate, :telephoneNumber, :password, :disabled, :loginCounter)").bind("id", id).bind("senderId", senderId).bind("serviceId", serviceId).bind("roleId", roleId).bind("email", email).bind("code", code).bind("otpKey", otpKey).bind("telephoneNumber", telephoneNumber).bind("password", password).bind("date", java.sql.Timestamp.from(date.toInstant())).bind("expiryDate", java.sql.Timestamp.from(expiryDate.toInstant())).bind("disabled", disabled).bind("loginCounter", loginCounter).execute());
        return this;
    }

    public uk.gov.pay.adminusers.utils.DatabaseTestHelper addServiceInvite(int id, int senderId, int roleId, java.lang.String email, java.lang.String code, java.lang.String otpKey, java.time.ZonedDateTime date, java.time.ZonedDateTime expiryDate, java.lang.String telephoneNumber, java.lang.String password, java.lang.Boolean disabled, java.lang.Integer loginCounter) {
        jdbi.withHandle(handle -> handle.createUpdate("INSERT INTO invites(id, sender_id, role_id, email, code, otp_key, date, expiry_date, telephone_number, password, disabled, login_counter, type) " + "VALUES (:id, :senderId, :roleId, :email, :code, :otpKey, :date, :expiryDate, :telephoneNumber, :password, :disabled, :loginCounter, :type)").bind("id", id).bind("senderId", senderId).bind("roleId", roleId).bind("email", email).bind("code", code).bind("otpKey", otpKey).bind("telephoneNumber", telephoneNumber).bind("password", password).bind("date", java.sql.Timestamp.from(date.toInstant())).bind("expiryDate", java.sql.Timestamp.from(expiryDate.toInstant())).bind("disabled", disabled).bind("loginCounter", loginCounter).bind("type", "service").execute());
        return this;
    }

    public java.util.List<java.util.Map<java.lang.String, java.lang.Object>> findInviteByCode(java.lang.String code) {
        return jdbi.withHandle(h -> h.createQuery("SELECT id, sender_id, service_id, role_id, email, code, otp_key, date, telephone_number, disabled, login_counter FROM invites " + "WHERE code = :code").bind("code", code).mapToMap().list());
    }

    public java.util.List<java.util.Map<java.lang.String, java.lang.Object>> findServiceByExternalId(java.lang.String serviceExternalId) {
        return jdbi.withHandle(h -> h.createQuery("SELECT * FROM services " + "WHERE external_id = :external_id").bind("external_id", serviceExternalId).mapToMap().list());
    }

    public java.util.List<java.util.Map<java.lang.String, java.lang.Object>> findServiceNameByServiceId(java.lang.Integer serviceId) {
        return jdbi.withHandle(h -> h.createQuery("SELECT * FROM service_names WHERE service_id = :serviceId").bind("serviceId", serviceId).mapToMap().list());
    }

    public java.util.List<java.util.Map<java.lang.String, java.lang.Object>> findStripeAgreementById(int id) {
        return jdbi.withHandle(handle -> handle.createQuery("SELECT * FROM stripe_agreements WHERE id = :id").bind("id", id).mapToMap().list());
    }

    public java.util.List<java.util.Map<java.lang.String, java.lang.Object>> findGovUkPayAgreementEntity(java.lang.Integer serviceId) {
        return jdbi.withHandle(handle -> handle.createQuery("SELECT * FROM govuk_pay_agreements WHERE service_id = :id").bind("id", serviceId).mapToMap().list());
    }

    public uk.gov.pay.adminusers.utils.DatabaseTestHelper insertGovUkPayAgreementEntity(int serviceId, java.lang.String email, java.time.ZonedDateTime agreementTime) {
        jdbi.withHandle(handle -> handle.createUpdate("INSERT INTO govuk_pay_agreements(service_id, agreement_time, email) VALUES (:serviceId, :agreementTime, :email)").bind("serviceId", serviceId).bind("email", email).bind("agreementTime", java.sql.Timestamp.from(agreementTime.toInstant())).execute());
        return this;
    }

    public uk.gov.pay.adminusers.utils.DatabaseTestHelper insertStripeAgreementEntity(int serviceId, java.time.ZonedDateTime agreementTime, java.lang.String ipAddress) {
        jdbi.withHandle(handle -> handle.createUpdate("INSERT INTO stripe_agreements(service_id, agreement_time, ip_address) VALUES (:serviceId, :agreementTime, :ipAddress)").bind("serviceId", serviceId).bind("agreementTime", java.sql.Timestamp.from(agreementTime.toInstant())).bind("ipAddress", ipAddress).execute());
        return this;
    }

    private uk.gov.pay.adminusers.utils.DatabaseTestHelper addServiceName(uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity entity, java.lang.Integer serviceId) {
        jdbi.withHandle(handle -> handle.createUpdate("INSERT INTO service_names(service_id, language, name) VALUES (:serviceId, :language, :name)").bind("serviceId", serviceId).bind("language", entity.getLanguage().toString()).bind("name", entity.getName()).execute());
        return this;
    }

    public uk.gov.pay.adminusers.utils.DatabaseTestHelper insertServiceEntity(uk.gov.pay.adminusers.persistence.entity.ServiceEntity serviceEntity) {
        jdbi.withHandle(handle -> {
            org.postgresql.util.PGobject customBranding = (serviceEntity.getCustomBranding() == null) ? null : new uk.gov.pay.adminusers.persistence.entity.CustomBrandingConverter().convertToDatabaseColumn(serviceEntity.getCustomBranding());
            uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity merchantDetails = serviceEntity.getMerchantDetailsEntity();
            return handle.createUpdate((((((("INSERT INTO services(" + "id, custom_branding, ") + "merchant_name, merchant_telephone_number, merchant_address_line1, merchant_address_line2, merchant_address_city, ") + "merchant_address_postcode, merchant_address_country, merchant_email, merchant_url, external_id, redirect_to_service_immediately_on_terminal_state, ") + "current_go_live_stage, experimental_features_enabled, current_psp_test_account_stage, created_date) ") + "VALUES (:id, :customBranding, :merchantName, :merchantTelephoneNumber, :merchantAddressLine1, :merchantAddressLine2, ") + ":merchantAddressCity, :merchantAddressPostcode, :merchantAddressCountry, :merchantEmail, :merchantUrl, :externalId, :redirectToServiceImmediatelyOnTerminalState, ") + ":currentGoLiveStage, :experimentalFeaturesEnabled, :pspTestAccountStage, :createdDate)").bind("id", serviceEntity.getId()).bindBySqlType("customBranding", customBranding, java.sql.Types.OTHER).bind("merchantName", merchantDetails.getName()).bind("merchantTelephoneNumber", merchantDetails.getTelephoneNumber()).bind("merchantAddressLine1", merchantDetails.getAddressLine1()).bind("merchantAddressLine2", merchantDetails.getAddressLine2()).bind("merchantAddressCity", merchantDetails.getAddressCity()).bind("merchantAddressPostcode", merchantDetails.getAddressPostcode()).bind("merchantAddressCountry", merchantDetails.getAddressCountryCode()).bind("merchantEmail", merchantDetails.getEmail()).bind("merchantUrl", merchantDetails.getUrl()).bind("externalId", serviceEntity.getExternalId()).bind("redirectToServiceImmediatelyOnTerminalState", serviceEntity.isRedirectToServiceImmediatelyOnTerminalState()).bind("currentGoLiveStage", serviceEntity.getCurrentGoLiveStage()).bind("experimentalFeaturesEnabled", serviceEntity.isExperimentalFeaturesEnabled()).bind("pspTestAccountStage", serviceEntity.getCurrentPspTestAccountStage()).bind("createdDate", serviceEntity.getCreatedDate()).execute();
        });
        serviceEntity.getGatewayAccountIds().forEach(gatewayAccount -> jdbi.withHandle(handle -> handle.createUpdate("INSERT INTO service_gateway_accounts(service_id, gateway_account_id) VALUES (:serviceId, :gatewayAccountId)").bind("serviceId", serviceEntity.getId()).bind("gatewayAccountId", gatewayAccount.getGatewayAccountId()).execute()));
        serviceEntity.getServiceNames().values().forEach(name -> addServiceName(name, serviceEntity.getId()));
        return this;
    }

    public void truncateAllData() {
        jdbi.withHandle(handle -> handle.createUpdate("TRUNCATE TABLE users CASCADE").execute());
        jdbi.withHandle(handle -> handle.createUpdate("TRUNCATE TABLE services CASCADE").execute());
    }
}
