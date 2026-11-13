package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected an input source directory");
        }

        Path input = Paths.get(args[0]);
        Path output = args.length > 1 ? Paths.get(args[1]) : input;

        Files.walk(input)
                .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        Path relative = input.relativize(path);
                        Path target = output.resolve(relative);
                        Files.createDirectories(target.getParent());
                        Files.writeString(target, transform(Files.readString(path)), StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private static String transform(String source) {
        String updated = source;

        updated = updated.replace("import javax.servlet.", "import jakarta.servlet.");
        updated = updated.replace("import javax.servlet;", "import jakarta.servlet;");
        updated = updated.replace("import org.eclipse.jetty.server.nio.SelectChannelConnector;\n", "");
        updated = updated.replace("import org.eclipse.jetty.server.Connector;", "import org.eclipse.jetty.server.ServerConnector;");
        updated = updated.replace("import org.eclipse.jetty.server.Server;", "import org.eclipse.jetty.server.Server;\nimport org.eclipse.jetty.server.HttpConfiguration;\nimport org.eclipse.jetty.server.HttpConnectionFactory;");

        updated = updated.replace("private final Connector httpConnector;", "private final ServerConnector httpConnector;");

        updated = updated.replace(
                "        this.server = new Server();\n        this.server.setSendServerVersion(false);\n        this.server.setSendDateHeader(true);\n\n        this.httpConnector = new SelectChannelConnector();\n        this.httpConnector.setPort(port);\n        server.addConnector(this.httpConnector);",
                "        this.server = new Server();\n        HttpConfiguration httpConfiguration = new HttpConfiguration();\n        httpConfiguration.setSendServerVersion(false);\n        httpConfiguration.setSendDateHeader(true);\n\n        this.httpConnector = new ServerConnector(server, new HttpConnectionFactory(httpConfiguration));\n        this.httpConnector.setPort(port);\n        server.addConnector(this.httpConnector);");

        updated = updated.replace("this.server.setSendServerVersion(false);", "httpConfiguration.setSendServerVersion(false);");
        updated = updated.replace("this.server.setSendDateHeader(true);", "httpConfiguration.setSendDateHeader(true);");

        updated = updated.replace("this.httpConfiguration", "httpConfiguration");

        return updated;
    }
}
