package github.chains;

import spoon.Launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: <input-source-dir> <output-source-dir>");
        }

        Path inputDir = Path.of(args[0]);
        Path outputDir = Path.of(args[1]);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(inputDir.toString());
        launcher.buildModel();

        Files.walk(inputDir)
                .filter(path -> path.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .forEach(path -> {
                    try {
                        Path relative = inputDir.relativize(path);
                        Path target = outputDir.resolve(relative);
                        Files.createDirectories(target.getParent());
                        String rewritten = rewrite(Files.readString(path, StandardCharsets.UTF_8));
                        Files.writeString(target, rewritten, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private static String rewrite(String source) {
        String updated = source;

        updated = updated.replace("import org.eclipse.jetty.server.nio.SelectChannelConnector;",
                "import org.eclipse.jetty.server.HttpConfiguration;\nimport org.eclipse.jetty.server.HttpConnectionFactory;\nimport org.eclipse.jetty.server.ServerConnector;");
        updated = updated.replace("org.eclipse.jetty.server.nio.SelectChannelConnector", "org.eclipse.jetty.server.ServerConnector");
        updated = updated.replace("import org.eclipse.jetty.server.Connector;", "import org.eclipse.jetty.server.ServerConnector;");
        updated = updated.replace("import javax.servlet.", "import jakarta.servlet.");
        updated = updated.replace("javax.servlet.", "jakarta.servlet.");

        updated = updated.replace("this.server.setSendServerVersion(false);", "final HttpConfiguration httpConfiguration = new HttpConfiguration();\n        httpConfiguration.setSendServerVersion(false);");
        updated = updated.replace("this.server.setSendDateHeader(true);", "httpConfiguration.setSendDateHeader(true);");
        updated = updated.replace("this.httpConnector = new SelectChannelConnector();", "this.httpConnector = new ServerConnector(this.server, new HttpConnectionFactory(httpConfiguration));");
        updated = updated.replace("this.httpConnector.setPort(port);", "this.httpConnector.setPort(port);");
        updated = updated.replace("server.addConnector(this.httpConnector);", "server.addConnector(this.httpConnector);");
        updated = updated.replace("return httpConnector.getLocalPort();", "return ((ServerConnector) httpConnector).getLocalPort();");

        return updated;
    }
}
