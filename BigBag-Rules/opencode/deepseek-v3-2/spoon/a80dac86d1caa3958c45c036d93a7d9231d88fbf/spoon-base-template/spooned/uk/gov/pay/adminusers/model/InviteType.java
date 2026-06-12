package uk.gov.pay.adminusers.model;
public enum InviteType {

    USER("user"),
    SERVICE("service");

    private java.lang.String type;

    InviteType(java.lang.String type) {
        this.type = type;
    }

    public java.lang.String getType() {
        return type;
    }

    public static uk.gov.pay.adminusers.model.InviteType from(java.lang.String inviteType) {
        if (uk.gov.pay.adminusers.model.InviteType.USER.type.equals(inviteType)) {
            return uk.gov.pay.adminusers.model.InviteType.USER;
        } else if (uk.gov.pay.adminusers.model.InviteType.SERVICE.type.equals(inviteType)) {
            return uk.gov.pay.adminusers.model.InviteType.SERVICE;
        } else {
            throw new java.lang.RuntimeException(java.lang.String.format("invalid invite type: [%s]", inviteType));
        }
    }
}
