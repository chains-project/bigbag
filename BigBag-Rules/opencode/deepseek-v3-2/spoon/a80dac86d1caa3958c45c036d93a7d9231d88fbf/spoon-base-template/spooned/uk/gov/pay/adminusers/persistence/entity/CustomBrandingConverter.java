package uk.gov.pay.adminusers.persistence.entity;
@javax.persistence.Converter
public class CustomBrandingConverter implements javax.persistence.AttributeConverter<java.util.Map<java.lang.String, java.lang.Object>, org.postgresql.util.PGobject> {
    private static com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @java.lang.Override
    public org.postgresql.util.PGobject convertToDatabaseColumn(java.util.Map<java.lang.String, java.lang.Object> customBranding) {
        org.postgresql.util.PGobject dbCustomBranding = new org.postgresql.util.PGobject();
        dbCustomBranding.setType("json");
        try {
            dbCustomBranding.setValue(uk.gov.pay.adminusers.persistence.entity.CustomBrandingConverter.objectMapper.writeValueAsString(customBranding));
        } catch (java.sql.SQLException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new java.lang.RuntimeException(e);
        }
        return dbCustomBranding;
    }

    @java.lang.Override
    public java.util.Map<java.lang.String, java.lang.Object> convertToEntityAttribute(org.postgresql.util.PGobject dbCustomBranding) {
        try {
            if ((dbCustomBranding == null) || org.apache.commons.lang3.StringUtils.isEmpty(dbCustomBranding.toString())) {
                return null;
            } else {
                return uk.gov.pay.adminusers.persistence.entity.CustomBrandingConverter.objectMapper.readValue(dbCustomBranding.toString(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
            }
        } catch (java.io.IOException e) {
            throw new java.lang.RuntimeException(e);
        }
    }
}
