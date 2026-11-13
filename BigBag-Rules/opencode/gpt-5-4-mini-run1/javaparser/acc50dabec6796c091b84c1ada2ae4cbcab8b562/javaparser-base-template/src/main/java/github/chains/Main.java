package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

public class Main {

    private static final Map<String, String> TYPE_RENAMES = new LinkedHashMap<>();

    static {
        TYPE_RENAMES.put(
                "org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter",
                "org.apache.struts2.dispatcher.filter.StrutsPrepareAndExecuteFilter");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source directory argument");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Not a directory: " + sourceRoot);
        }

        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11);

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            transformFile(path);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to transform " + path, e);
                        }
                    });
        }
    }

    private static void transformFile(Path file) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(file);
        LexicalPreservingPrinter.setup(cu);

        boolean changed = false;
        for (Map.Entry<String, String> rename : TYPE_RENAMES.entrySet()) {
            changed |= renameImports(cu, rename.getKey(), rename.getValue());
            changed |= renameQualifiedTypes(cu, rename.getKey(), rename.getValue());
        }

        if (changed) {
            Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
        }
    }

    private static boolean renameImports(CompilationUnit cu, String oldName, String newName) {
        boolean changed = false;
        for (ImportDeclaration importDeclaration : cu.findAll(ImportDeclaration.class)) {
            if (importDeclaration.getNameAsString().equals(oldName)) {
                importDeclaration.setName(newName);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean renameQualifiedTypes(CompilationUnit cu, String oldName, String newName) {
        boolean changed = false;
        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            if (type.toString().equals(oldName)) {
                ClassOrInterfaceType replacement = StaticJavaParser.parseClassOrInterfaceType(newName);
                type.replace(replacement);
                changed = true;
            }
        }
        return changed;
    }
}
