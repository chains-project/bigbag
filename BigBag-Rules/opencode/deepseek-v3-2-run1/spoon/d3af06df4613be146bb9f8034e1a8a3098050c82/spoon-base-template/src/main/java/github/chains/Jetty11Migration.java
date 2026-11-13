package github.chains;

/**
 * GENERIC TRANSFORMATION RULE for Jetty 9/10 to Jetty 11+ migration
 * 
 * SUMMARY OF BREAKING CHANGES:
 * 
 * 1. SelectChannelConnector class removed → replaced by ServerConnector
 *    - Old: new SelectChannelConnector()
 *    - New: new ServerConnector(server, httpFactory)
 *    - Requires: Import org.eclipse.jetty.server.HttpConfiguration
 *                Import org.eclipse.jetty.server.HttpConnectionFactory
 *    
 * 2. Server.setSendServerVersion() and Server.setSendDateHeader() removed
 *    - These methods moved to HttpConfiguration class
 *    - Old: server.setSendServerVersion(false); server.setSendDateHeader(true);
 *    - New: 
 *        HttpConfiguration httpConfig = new HttpConfiguration();
 *        httpConfig.setSendServerVersion(false);
 *        httpConfig.setSendDateHeader(true);
 *        HttpConnectionFactory httpFactory = new HttpConnectionFactory(httpConfig);
 *        
 * 3. javax.servlet → jakarta.servlet package change
 *    - All javax.servlet.* imports → jakarta.servlet.*
 *    - Affects: HttpServletRequest, HttpServletResponse, ServletException imports
 *    
 * 4. Connector.setPort() and Connector.getLocalPort() accessibility
 *    - setPort() and getLocalPort() are in AbstractNetworkConnector class
 *    - ServerConnector extends AbstractNetworkConnector
 *    - Need to cast Connector to ServerConnector or change variable type
 *    - Old: httpConnector.setPort(port); httpConnector.getLocalPort()
 *    - New: ((ServerConnector) httpConnector).setPort(port);
 *           ((ServerConnector) httpConnector).getLocalPort()
 *    
 * GENERIC TRANSFORMATION PATTERNS:
 * 
 * Pattern 1: Import replacement
 *   javax.servlet.* → jakarta.servlet.*
 *   org.eclipse.jetty.server.nio.SelectChannelConnector → org.eclipse.jetty.server.ServerConnector
 *   
 * Pattern 2: Constructor replacement with configuration
 *   new SelectChannelConnector() → 
 *     HttpConfiguration httpConfig = new HttpConfiguration();
 *     httpConfig.setSendServerVersion(false);
 *     httpConfig.setSendDateHeader(true);
 *     HttpConnectionFactory httpFactory = new HttpConnectionFactory(httpConfig);
 *     new ServerConnector(server, httpFactory)
 *     
 * Pattern 3: Method call updates
 *   server.setSendServerVersion(false) → httpConfig.setSendServerVersion(false)
 *   server.setSendDateHeader(true) → httpConfig.setSendDateHeader(true)
 *   connector.setPort(port) → ((ServerConnector) connector).setPort(port)
 *   connector.getLocalPort() → ((ServerConnector) connector).getLocalPort()
 *   
 * Pattern 4: Type casting for Connector variables
 *   If variable is declared as Connector but needs ServerConnector methods,
 *   add cast: (ServerConnector) connector
 *   
 * USAGE:
 * This transformation should be applied to any Maven/Java project migrating
 * from Jetty 9/10 to Jetty 11+.
 * 
 * The transformation handles the most common breaking changes but may need
 * manual adjustments for complex cases.
 */
public class Jetty11Migration {
    
    public static void main(String[] args) {
        System.out.println("Jetty 11 Migration Transformation Rules");
        System.out.println("========================================");
        System.out.println();
        System.out.println("This file contains the generic transformation rules for migrating");
        System.out.println("from Jetty 9/10 to Jetty 11+.");
        System.out.println();
        System.out.println("Key transformations needed:");
        System.out.println("1. Update javax.servlet to jakarta.servlet imports");
        System.out.println("2. Replace SelectChannelConnector with ServerConnector");
        System.out.println("3. Move server configuration to HttpConfiguration");
        System.out.println("4. Add appropriate type casting for connector methods");
        System.out.println();
        System.out.println("Example transformation for a typical Jetty server setup:");
        System.out.println();
        System.out.println("BEFORE:");
        System.out.println("  import org.eclipse.jetty.server.nio.SelectChannelConnector;");
        System.out.println("  import javax.servlet.http.HttpServletRequest;");
        System.out.println("  ...");
        System.out.println("  Server server = new Server();");
        System.out.println("  server.setSendServerVersion(false);");
        System.out.println("  server.setSendDateHeader(true);");
        System.out.println("  Connector connector = new SelectChannelConnector();");
        System.out.println("  connector.setPort(port);");
        System.out.println("  int localPort = connector.getLocalPort();");
        System.out.println();
        System.out.println("AFTER:");
        System.out.println("  import org.eclipse.jetty.server.ServerConnector;");
        System.out.println("  import org.eclipse.jetty.server.HttpConfiguration;");
        System.out.println("  import org.eclipse.jetty.server.HttpConnectionFactory;");
        System.out.println("  import jakarta.servlet.http.HttpServletRequest;");
        System.out.println("  ...");
        System.out.println("  Server server = new Server();");
        System.out.println("  HttpConfiguration httpConfig = new HttpConfiguration();");
        System.out.println("  httpConfig.setSendServerVersion(false);");
        System.out.println("  httpConfig.setSendDateHeader(true);");
        System.out.println("  HttpConnectionFactory httpFactory = new HttpConnectionFactory(httpConfig);");
        System.out.println("  ServerConnector connector = new ServerConnector(server, httpFactory);");
        System.out.println("  connector.setPort(port);");
        System.out.println("  int localPort = connector.getLocalPort();");
    }
}