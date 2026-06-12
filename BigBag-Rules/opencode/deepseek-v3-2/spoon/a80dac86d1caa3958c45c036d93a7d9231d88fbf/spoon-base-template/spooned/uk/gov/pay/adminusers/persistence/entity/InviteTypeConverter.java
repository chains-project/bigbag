package uk.gov.pay.adminusers.persistence.entity;
@javax.persistence.Converter
public class InviteTypeConverter implements javax.persistence.AttributeConverter<uk.gov.pay.adminusers.model.InviteType, java.lang.String> {
    @java.lang.Override
    public java.lang.String convertToDatabaseColumn(uk.gov.pay.adminusers.model.InviteType inviteType) {
        return inviteType.getType();
    }

    @java.lang.Override
    public uk.gov.pay.adminusers.model.InviteType convertToEntityAttribute(java.lang.String string) {
        return uk.gov.pay.adminusers.model.InviteType.from(string);
    }
}
