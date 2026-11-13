package github;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import github.chains.JettyUpgradeChain;
import org.junit.jupiter.api.Test;
import java.io.FileInputStream;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

public class JettyUpgradeTest {
    
    @Test
    public void testSelectChannelConnectorReplacement() throws IOException {
        // Create a sample Java file with deprecated SelectChannelConnector
        String sampleCode = 
            "import org.eclipse.jetty.server.SelectChannelConnector;\n" +
            "import org.eclipse.jetty.server.Server;\n" +
            "\n" +
            "public class TestClass {\n" +
            "    public void setupServer() {\n" +
            "        Server server = new Server();\n" +
            "        SelectChannelConnector connector = new SelectChannelConnector();\n" +
            "        server.addConnector(connector);\n" +
            "    }\n" +
            "}\n";
        
        CompilationUnit cu = StaticJavaParser.parse(sampleCode);
        JettyUpgradeChain chain = new JettyUpgradeChain();
        CompilationUnit result = chain.apply(cu).get();
        
        // Verify that the deprecated class was replaced
        assertTrue(result.toString().contains("ServerConnector"));
        assertFalse(result.toString().contains("SelectChannelConnector"));
    }
}