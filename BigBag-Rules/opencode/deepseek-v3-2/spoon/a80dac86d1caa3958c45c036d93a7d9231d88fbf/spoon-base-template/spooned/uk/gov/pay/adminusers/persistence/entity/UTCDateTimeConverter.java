package uk.gov.pay.adminusers.persistence.entity;
@javax.persistence.Converter
public class UTCDateTimeConverter implements javax.persistence.AttributeConverter<java.time.ZonedDateTime, java.sql.Timestamp> {
    public static final java.time.ZoneId UTC = java.time.ZoneOffset.UTC;

    @java.lang.Override
    public java.sql.Timestamp convertToDatabaseColumn(java.time.ZonedDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return java.sql.Timestamp.from(dateTime.toInstant());
    }

    @java.lang.Override
    public java.time.ZonedDateTime convertToEntityAttribute(java.sql.Timestamp s) {
        if (s == null) {
            return null;
        }
        return java.time.ZonedDateTime.ofInstant(s.toInstant(), uk.gov.pay.adminusers.persistence.entity.UTCDateTimeConverter.UTC);
    }
}
