package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static final Pattern FLYWAY_DECL = Pattern.compile(
            "^(\\s*)(final\\s+)?((?:org\\.flywaydb\\.core\\.)?Flyway)\\s+(\\w+)\\s*=\\s*new\\s+(?:org\\.flywaydb\\.core\\.)?Flyway\\s*\\(\\)\\s*;\\s*$");
    private static final Pattern SETTER = Pattern.compile(
            "^(\\s*)(\\w+)\\.(setDataSource|setClassLoader|setLocations|setValidateOnMigrate)\\s*\\((.*)\\)\\s*;\\s*$");

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected <inputDir> <outputDir>");
        }

        Path input = Paths.get(args[0]);
        Path output = Paths.get(args[1]);
        copyAndTransform(input, output);
    }

    private static void copyAndTransform(Path input, Path output) throws IOException {
        Files.walk(input).forEach(source -> {
            try {
                Path target = output.resolve(input.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else if (source.toString().endsWith(".java")) {
                    Path parent = target.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.writeString(target, transformJava(Files.readString(source, StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
                } else {
                    Path parent = target.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static String transformJava(String source) {
        String[] lines = source.split("\\R", -1);
        List<String> out = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher decl = FLYWAY_DECL.matcher(line);
            if (!decl.matches()) {
                out.add(line);
                continue;
            }

            String indent = decl.group(1);
            String type = decl.group(3);
            String var = decl.group(4);

            List<String> args = new ArrayList<>();
            List<String> kinds = new ArrayList<>();

            int j = i + 1;
            while (j < lines.length) {
                Matcher setter = SETTER.matcher(lines[j]);
                if (!setter.matches() || !var.equals(setter.group(2))) {
                    break;
                }
                kinds.add(setter.group(3));
                args.add(setter.group(4));
                j++;
            }

            if (kinds.isEmpty()) {
                out.add(line);
                continue;
            }

            StringBuilder replacement = new StringBuilder();
            replacement.append(indent);
            if (decl.group(2) != null) {
                replacement.append("final ");
            }
            replacement.append(type).append(' ').append(var).append(" = ");

            String classLoader = null;
            StringBuilder chain = new StringBuilder();
            for (int k = 0; k < kinds.size(); k++) {
                String kind = kinds.get(k);
                String arg = args.get(k);
                if ("setClassLoader".equals(kind)) {
                    classLoader = arg;
                }
            }
            chain.append("org.flywaydb.core.Flyway.configure(");
            if (classLoader != null) {
                chain.append(classLoader);
            }
            chain.append(")");
            for (int k = 0; k < kinds.size(); k++) {
                String kind = kinds.get(k);
                String arg = args.get(k);
                if ("setClassLoader".equals(kind)) {
                    continue;
                } else if ("setDataSource".equals(kind)) {
                    chain.append(".dataSource(").append(arg).append(')');
                } else if ("setLocations".equals(kind)) {
                    chain.append(".locations(").append(arg).append(')');
                } else if ("setValidateOnMigrate".equals(kind)) {
                    chain.append(".validateOnMigrate(").append(arg).append(')');
                }
            }
            replacement.append(chain).append(".load();");
            out.add(replacement.toString());
            i = j - 1;
        }

        return String.join(System.lineSeparator(), out);
    }
}
