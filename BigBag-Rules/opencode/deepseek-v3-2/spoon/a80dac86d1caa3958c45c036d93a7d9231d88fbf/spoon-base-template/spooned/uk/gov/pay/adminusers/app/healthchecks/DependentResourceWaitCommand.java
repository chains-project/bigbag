package uk.gov.pay.adminusers.app.healthchecks;
public class DependentResourceWaitCommand extends io.dropwizard.cli.ConfiguredCommand<uk.gov.pay.adminusers.app.config.AdminUsersConfig> {
    public DependentResourceWaitCommand() {
        super("waitOnDependencies", "Waits for dependent resources to become available");
    }

    @java.lang.Override
    public void configure(net.sourceforge.argparse4j.inf.Subparser subparser) {
        super.configure(subparser);
    }

    @java.lang.Override
    protected void run(io.dropwizard.setup.Bootstrap<uk.gov.pay.adminusers.app.config.AdminUsersConfig> bs, net.sourceforge.argparse4j.inf.Namespace ns, uk.gov.pay.adminusers.app.config.AdminUsersConfig conf) {
        new uk.gov.service.payments.commons.utils.startup.ApplicationStartupDependentResourceChecker(new uk.gov.service.payments.commons.utils.startup.DatabaseStartupResource(conf.getDataSourceFactory())).checkAndWaitForResource();
    }
}
