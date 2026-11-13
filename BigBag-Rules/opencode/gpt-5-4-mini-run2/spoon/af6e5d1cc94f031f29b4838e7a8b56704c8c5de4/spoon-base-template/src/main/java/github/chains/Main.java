package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    private static final Pattern DECLARATION = Pattern.compile(
            "^(\\s*)(final\\s+)?([\\w.$<>\\[\\], ?]+?)\\s+(\\w+)\\s*=\\s*new\\s+Flyway\\s*\\(\\s*\\)\\s*;\\s*$");
    private static final Pattern SETTER = Pattern.compile(
            "^(\\s*)(\\w+)\\.(setClassLoader|setDataSource|setLocations|setValidateOnMigrate)\\s*\\((.*?)\\)\\s*;\\s*$");

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("usage: Main <input-source-dir>");
        }

        final Path root = Paths.get(args[0]);
        Files.walk(root)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::transformFile);
    }

    private static void transformFile(final Path file) {
        try {
            final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            final StringBuilder out = new StringBuilder();
            for (int i = 0; i < lines.size(); i++) {
                final Matcher declaration = DECLARATION.matcher(lines.get(i));
                if (!declaration.matches()) {
                    out.append(lines.get(i)).append('\n');
                    continue;
                }

                final String indent = declaration.group(1);
                final String finalKeyword = declaration.group(2) == null ? "" : declaration.group(2);
                final String type = declaration.group(3);
                final String name = declaration.group(4);

                int j = i + 1;
                String classLoaderArg = null;
                String dataSourceArg = null;
                String locationsArg = null;
                String validateArg = null;

                while (j < lines.size()) {
                    final Matcher setter = SETTER.matcher(lines.get(j));
                    if (!setter.matches() || !name.equals(setter.group(2))) {
                        break;
                    }
                    final String method = setter.group(3);
                    final String arg = setter.group(4).trim();
                    if ("setClassLoader".equals(method)) {
                        classLoaderArg = arg;
                    } else if ("setDataSource".equals(method)) {
                        dataSourceArg = arg;
                    } else if ("setLocations".equals(method)) {
                        locationsArg = arg;
                    } else if ("setValidateOnMigrate".equals(method)) {
                        validateArg = arg;
                    }
                    j++;
                }

                if (dataSourceArg == null && locationsArg == null && validateArg == null && classLoaderArg == null) {
                    out.append(lines.get(i)).append('\n');
                    continue;
                }

                final StringBuilder expr = new StringBuilder();
                expr.append(indent).append(finalKeyword).append(type).append(' ').append(name).append(" = org.flywaydb.core.Flyway.configure(");
                if (classLoaderArg != null) {
                    expr.append(classLoaderArg);
                }
                expr.append(")");
                if (dataSourceArg != null) {
                    expr.append(".dataSource(").append(dataSourceArg).append(')');
                }
                if (locationsArg != null) {
                    expr.append(".locations(").append(locationsArg).append(')');
                }
                if (validateArg != null) {
                    expr.append(".validateOnMigrate(").append(validateArg).append(')');
                }
                expr.append(".load();");

                out.append(expr).append('\n');
                i = j - 1;
            }
            Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("failed to transform " + file, e);
        }
    }
}
