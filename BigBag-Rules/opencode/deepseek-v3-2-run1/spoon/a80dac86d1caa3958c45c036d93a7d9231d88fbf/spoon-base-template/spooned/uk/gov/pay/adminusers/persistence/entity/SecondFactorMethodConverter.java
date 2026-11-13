package uk.gov.pay.adminusers.persistence.entity;
@javax.persistence.Converter
public class SecondFactorMethodConverter implements javax.persistence.AttributeConverter<uk.gov.pay.adminusers.model.SecondFactorMethod, java.lang.String> {
    @java.lang.Override
    public java.lang.String convertToDatabaseColumn(uk.gov.pay.adminusers.model.SecondFactorMethod secondFactor) {
        return secondFactor.toString();
    }

    @java.lang.Override
    public uk.gov.pay.adminusers.model.SecondFactorMethod convertToEntityAttribute(java.lang.String string) {
        for (uk.gov.pay.adminusers.model.SecondFactorMethod secondFactorMethod : uk.gov.pay.adminusers.model.SecondFactorMethod.values()) {
            if (secondFactorMethod.toString().equals(string)) {
                return secondFactorMethod;
            }
        }
        return uk.gov.pay.adminusers.model.SecondFactorMethod.SMS;
    }
}
