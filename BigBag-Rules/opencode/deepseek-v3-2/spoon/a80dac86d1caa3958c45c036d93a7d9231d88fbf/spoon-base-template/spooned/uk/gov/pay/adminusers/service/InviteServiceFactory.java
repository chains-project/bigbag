package uk.gov.pay.adminusers.service;
public interface InviteServiceFactory {
    uk.gov.pay.adminusers.service.ServiceInviteCreator serviceInvite();

    uk.gov.pay.adminusers.service.UserInviteCreator userInvite();

    uk.gov.pay.adminusers.service.InviteFinder inviteFinder();

    uk.gov.pay.adminusers.service.InviteRouter inviteCompleteRouter();

    uk.gov.pay.adminusers.service.ServiceInviteCompleter completeServiceInvite();

    uk.gov.pay.adminusers.service.UserInviteCompleter completeUserInvite();

    uk.gov.pay.adminusers.service.InviteRouter inviteOtpRouter();

    uk.gov.pay.adminusers.service.ServiceOtpDispatcher dispatchServiceOtp();

    uk.gov.pay.adminusers.service.UserOtpDispatcher dispatchUserOtp();
}
