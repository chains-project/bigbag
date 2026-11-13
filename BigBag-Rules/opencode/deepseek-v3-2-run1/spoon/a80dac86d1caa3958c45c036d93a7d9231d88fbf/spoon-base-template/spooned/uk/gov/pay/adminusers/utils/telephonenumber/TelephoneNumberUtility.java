package uk.gov.pay.adminusers.utils.telephonenumber;
public class TelephoneNumberUtility {
    private static final java.lang.String DEFAULT_COUNTRY = "GB";

    private static final com.google.i18n.phonenumbers.PhoneNumberUtil PHONE_NUMBER_UTIL = com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance();

    public static boolean isValidPhoneNumber(java.lang.String telephoneNumber) {
        try {
            if (org.apache.commons.lang3.StringUtils.isNotBlank(telephoneNumber)) {
                com.google.i18n.phonenumbers.Phonenumber.PhoneNumber phoneNumber = uk.gov.pay.adminusers.utils.telephonenumber.TelephoneNumberUtility.PHONE_NUMBER_UTIL.parseAndKeepRawInput(telephoneNumber, uk.gov.pay.adminusers.utils.telephonenumber.TelephoneNumberUtility.DEFAULT_COUNTRY);
                return uk.gov.pay.adminusers.utils.telephonenumber.TelephoneNumberUtility.PHONE_NUMBER_UTIL.isValidNumber(phoneNumber);
            }
            return false;
        } catch (com.google.i18n.phonenumbers.NumberParseException e) {
            return false;
        }
    }

    public static java.lang.String formatToE164(java.lang.String telephoneNumber) {
        try {
            com.google.i18n.phonenumbers.Phonenumber.PhoneNumber phoneNumber = uk.gov.pay.adminusers.utils.telephonenumber.TelephoneNumberUtility.PHONE_NUMBER_UTIL.parseAndKeepRawInput(telephoneNumber, uk.gov.pay.adminusers.utils.telephonenumber.TelephoneNumberUtility.DEFAULT_COUNTRY);
            return uk.gov.pay.adminusers.utils.telephonenumber.TelephoneNumberUtility.PHONE_NUMBER_UTIL.format(phoneNumber, com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (com.google.i18n.phonenumbers.NumberParseException e) {
            throw new java.lang.RuntimeException(e);
        }
    }
}
