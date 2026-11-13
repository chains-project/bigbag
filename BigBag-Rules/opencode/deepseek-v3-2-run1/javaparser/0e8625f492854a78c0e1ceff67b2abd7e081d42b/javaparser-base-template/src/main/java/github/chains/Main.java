package github.chains;

/**
 * Main class demonstrating the Jetty migration transformation.
 * For actual use, use JettyMigrationTransformer directly.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("Jetty 8/9 to Jetty 11 Migration Transformer");
        System.out.println("=============================================");
        System.out.println();
        System.out.println("This tool provides a generic, reusable transformation rule for");
        System.out.println("fixing breaking changes when upgrading from Jetty 8/9 to Jetty 11.");
        System.out.println();
        System.out.println("Breaking changes addressed:");
        System.out.println("1. SelectChannelConnector -> ServerConnector with HttpConfiguration");
        System.out.println("2. Server.setSendServerVersion/setSendDateHeader -> HttpConfiguration.setSendServerVersion/setSendDateHeader");
        System.out.println("3. javax.servlet -> jakarta.servlet package migration");
        System.out.println();
        System.out.println("Usage: java github.chains.JettyMigrationTransformer <source-directory>");
        System.out.println("Example: java -cp target/classes github.chains.JettyMigrationTransformer /path/to/project/src");
        System.out.println();
        System.out.println("Or compile to JAR and run:");
        System.out.println("mvn package");
        System.out.println("java -jar target/javaparser-1.0-SNAPSHOT.jar <source-directory>");
    }
}