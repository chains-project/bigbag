package uk.gov.pay.adminusers.model;
public enum SecondFactorMethod {

    SMS() {
        @java.lang.Override
        public java.lang.String toString() {
            return "sms";
        }
    },
    APP() {
        @java.lang.Override
        public java.lang.String toString() {
            return "app";
        }
    };
}
