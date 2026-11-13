package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    private static final String OLD_POINT_DISTANCE_FUNCTION = "org.tinspin.index.PointDistanceFunction";
    private static final String NEW_POINT_DISTANCE = "org.tinspin.index.PointDistance";
    private static final String OLD_POINT_ENTRY_DIST = "org.tinspin.index.PointEntryDist";
    private static final String NEW_POINT_ENTRY_KNN = "org.tinspin.index.Index.PointEntryKnn";
    private static final String OLD_POINT_INDEX = "org.tinspin.index.PointIndex";
    private static final String NEW_POINT_MAP = "org.tinspin.index.PointMap";

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: Main <input-source-dir> <output-source-dir>");
            System.exit(1);
        }

        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        Files.createDirectories(output);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.addInputResource(input.toString());
        launcher.buildModel();

        List<Path> filesToRewrite = launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class)).stream()
                .map(inv -> inv.getPosition() == null || inv.getPosition().getFile() == null ? null : inv.getPosition().getFile().toPath())
                .filter(p -> p != null)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));

        Files.walk(input).forEach(source -> {
            try {
                Path target = output.resolve(input.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                    return;
                }
                if (!source.toString().endsWith(".java")) {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    return;
                }

                String text = Files.readString(source, StandardCharsets.UTF_8);
                String rewritten = rewriteSource(text, source, filesToRewrite);
                Files.createDirectories(target.getParent());
                Files.writeString(target, rewritten, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static String rewriteSource(String source, Path file, List<Path> filesToRewrite) {
        String text = source;

        text = text.replace(OLD_POINT_DISTANCE_FUNCTION, NEW_POINT_DISTANCE);
        text = text.replace(OLD_POINT_ENTRY_DIST, NEW_POINT_ENTRY_KNN);
        text = text.replace(OLD_POINT_INDEX, NEW_POINT_MAP);
        text = text.replace("query1NN", "query1nn");

        if (filesToRewrite.contains(file)) {
            text = rewriteKdtreeCreateCalls(text);
        }

        return text;
    }

    private static String rewriteKdtreeCreateCalls(String source) {
        String marker = "KDTree.create(";
        StringBuilder out = new StringBuilder(source.length());
        int idx = 0;
        while (true) {
            int start = source.indexOf(marker, idx);
            if (start < 0) {
                out.append(source.substring(idx));
                break;
            }
            out.append(source, idx, start);
            int open = start + marker.length() - 1;
            int close = findMatchingParen(source, open);
            if (close < 0) {
                out.append(source.substring(start));
                break;
            }

            String args = source.substring(open + 1, close);
            String firstArg = firstTopLevelArgument(args);
            out.append("KDTree.create(").append(firstArg).append(")");
            idx = close + 1;
        }
        return out.toString();
    }

    private static int findMatchingParen(String source, int openIndex) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = openIndex; i < source.length(); i++) {
            char c = source.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String firstTopLevelArgument(String args) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '(' || c == '[' || c == '{' || c == '<') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}' || c == '>') {
                depth--;
            } else if (c == ',' && depth == 0) {
                return args.substring(0, i).trim();
            }
        }
        return args.trim();
    }
}
