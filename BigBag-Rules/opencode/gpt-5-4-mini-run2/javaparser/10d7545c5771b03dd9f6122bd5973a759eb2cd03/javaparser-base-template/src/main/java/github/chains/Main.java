package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BinaryExpr.Operator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Main {
    private static final String OLD_DROPWIZARD_SETUP = "io.dropwizard.setup";
    private static final String NEW_DROPWIZARD_SETUP = "io.dropwizard.core.setup";
    private static final String OLD_DROPWIZARD_APPLICATION = "io.dropwizard.Application";
    private static final String NEW_DROPWIZARD_APPLICATION = "io.dropwizard.core.Application";
    private static final String OLD_DROPWIZARD_CONFIGURATION = "io.dropwizard.Configuration";
    private static final String NEW_DROPWIZARD_CONFIGURATION = "io.dropwizard.core.Configuration";
    private static final String OLD_JAVAX_WS_RS = "javax.ws.rs";
    private static final String NEW_JAKARTA_WS_RS = "jakarta.ws.rs";
    private static final String OLD_JAVAX_VALIDATION = "javax.validation";
    private static final String NEW_JAKARTA_VALIDATION = "jakarta.validation";
    private static final String OLD_JAVAX_SERVLET = "javax.servlet";
    private static final String NEW_JAKARTA_SERVLET = "jakarta.servlet";
    private static final String OLD_JACKSON_JAXRS = "com.fasterxml.jackson.jaxrs";
    private static final String NEW_JACKSON_JAKARTA_RS = "com.fasterxml.jackson.jakarta.rs";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected a source root path");
        }

        Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Not a directory: " + root);
        }

        List<Path> javaFiles = new ArrayList<>();
        try {
            Files.walk(root)
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(javaFiles::add);
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan source tree", e);
        }

        for (Path file : javaFiles) {
            transform(file);
        }

        updatePom(root);
    }

    private static void transform(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            boolean changed = false;

            for (ImportDeclaration importDeclaration : cu.getImports()) {
                changed |= rewriteImport(importDeclaration, OLD_DROPWIZARD_APPLICATION, NEW_DROPWIZARD_APPLICATION);
                changed |= rewriteImport(importDeclaration, OLD_DROPWIZARD_CONFIGURATION, NEW_DROPWIZARD_CONFIGURATION);
                changed |= rewriteImport(importDeclaration, OLD_DROPWIZARD_SETUP, NEW_DROPWIZARD_SETUP);
                changed |= rewriteImport(importDeclaration, OLD_JAVAX_WS_RS, NEW_JAKARTA_WS_RS);
                changed |= rewriteImport(importDeclaration, OLD_JAVAX_VALIDATION, NEW_JAKARTA_VALIDATION);
                changed |= rewriteImport(importDeclaration, OLD_JAVAX_SERVLET, NEW_JAKARTA_SERVLET);
                changed |= rewriteImport(importDeclaration, OLD_JACKSON_JAXRS, NEW_JACKSON_JAKARTA_RS);
            }

            changed |= cu.findAll(ClassOrInterfaceType.class).stream()
                    .map(type -> rewriteType(type, OLD_DROPWIZARD_APPLICATION, NEW_DROPWIZARD_APPLICATION))
                    .reduce(Boolean.FALSE, Boolean::logicalOr);
            changed |= cu.findAll(ClassOrInterfaceType.class).stream()
                    .map(type -> rewriteType(type, OLD_DROPWIZARD_CONFIGURATION, NEW_DROPWIZARD_CONFIGURATION))
                    .reduce(Boolean.FALSE, Boolean::logicalOr);
            changed |= cu.findAll(ClassOrInterfaceType.class).stream()
                    .map(type -> rewriteType(type, OLD_DROPWIZARD_SETUP, NEW_DROPWIZARD_SETUP))
                    .reduce(Boolean.FALSE, Boolean::logicalOr);
            changed |= cu.findAll(ClassOrInterfaceType.class).stream()
                    .map(type -> rewriteType(type, OLD_JAVAX_WS_RS, NEW_JAKARTA_WS_RS))
                    .reduce(Boolean.FALSE, Boolean::logicalOr);
            changed |= cu.findAll(ClassOrInterfaceType.class).stream()
                    .map(type -> rewriteType(type, OLD_JAVAX_VALIDATION, NEW_JAKARTA_VALIDATION))
                    .reduce(Boolean.FALSE, Boolean::logicalOr);
            changed |= cu.findAll(ClassOrInterfaceType.class).stream()
                    .map(type -> rewriteType(type, OLD_JAVAX_SERVLET, NEW_JAKARTA_SERVLET))
                    .reduce(Boolean.FALSE, Boolean::logicalOr);
            changed |= cu.findAll(ClassOrInterfaceType.class).stream()
                    .map(type -> rewriteType(type, OLD_JACKSON_JAXRS, NEW_JACKSON_JAKARTA_RS))
                    .reduce(Boolean.FALSE, Boolean::logicalOr);

            changed |= cu.findAll(NameExpr.class).stream()
                    .map(name -> rewriteNameExpr(name, OLD_DROPWIZARD_APPLICATION, NEW_DROPWIZARD_APPLICATION))
                    .reduce(Boolean.FALSE, Boolean::logicalOr);
            changed |= cu.findAll(NameExpr.class).stream()
                    .map(name -> rewriteNameExpr(name, OLD_DROPWIZARD_CONFIGURATION, NEW_DROPWIZARD_CONFIGURATION))
                    .reduce(Boolean.FALSE, Boolean::logicalOr);
            changed |= cu.findAll(NameExpr.class).stream()
                    .map(name -> rewriteNameExpr(name, OLD_DROPWIZARD_SETUP, NEW_DROPWIZARD_SETUP))
                    .reduce(Boolean.FALSE, Boolean::logicalOr);
            changed |= cu.findAll(NameExpr.class).stream()
                    .map(name -> rewriteNameExpr(name, OLD_JAVAX_WS_RS, NEW_JAKARTA_WS_RS))
                    .reduce(Boolean.FALSE, Boolean::logicalOr);
            changed |= cu.findAll(NameExpr.class).stream()
                    .map(name -> rewriteNameExpr(name, OLD_JAVAX_VALIDATION, NEW_JAKARTA_VALIDATION))
                    .reduce(Boolean.FALSE, Boolean::logicalOr);
            changed |= cu.findAll(NameExpr.class).stream()
                    .map(name -> rewriteNameExpr(name, OLD_JAVAX_SERVLET, NEW_JAKARTA_SERVLET))
                    .reduce(Boolean.FALSE, Boolean::logicalOr);
            changed |= cu.findAll(NameExpr.class).stream()
                    .map(name -> rewriteNameExpr(name, OLD_JACKSON_JAXRS, NEW_JACKSON_JAKARTA_RS))
                    .reduce(Boolean.FALSE, Boolean::logicalOr);

            changed |= rewriteIsNullOrEmpty(cu);

            if (changed) {
                Files.writeString(file, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static boolean rewriteImport(ImportDeclaration importDeclaration, String oldPrefix, String newPrefix) {
        String name = importDeclaration.getNameAsString();
        if (name.equals(oldPrefix) || name.startsWith(oldPrefix + ".")) {
            importDeclaration.setName(name.replace(oldPrefix, newPrefix));
            return true;
        }
        return false;
    }

    private static boolean rewriteType(ClassOrInterfaceType type, String oldPrefix, String newPrefix) {
        String text = type.asString();
        if (text.equals(oldPrefix) || text.startsWith(oldPrefix + ".")) {
            type.replace(StaticJavaParser.parseClassOrInterfaceType(text.replace(oldPrefix, newPrefix)));
            return true;
        }
        return false;
    }

    private static boolean rewriteNameExpr(NameExpr nameExpr, String oldPrefix, String newPrefix) {
        String text = nameExpr.getNameAsString();
        if (text.equals(oldPrefix) || text.startsWith(oldPrefix + ".")) {
            nameExpr.setName(text.replace(oldPrefix, newPrefix));
            return true;
        }
        return false;
    }

    private static boolean rewriteIsNullOrEmpty(CompilationUnit cu) {
        boolean changed = false;
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (!call.getNameAsString().equals("isNullOrEmpty") || call.getArguments().size() != 1) {
                continue;
            }
            Expression argument = call.getArgument(0).clone();
            Expression replacement = new BinaryExpr(
                    new BinaryExpr(argument.clone(), new NameExpr("null"), Operator.EQUALS),
                    new MethodCallExpr(argument, "isEmpty"),
                    Operator.OR);
            call.replace(replacement);
            changed = true;
        }
        return changed;
    }

    private static void updatePom(Path start) {
        Optional<Path> pom = findPom(start);
        if (pom.isEmpty()) {
            return;
        }

        try (InputStream in = Files.newInputStream(pom.get())) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            Document document = factory.newDocumentBuilder().parse(in);
            org.w3c.dom.NodeList dependencies = document.getElementsByTagName("dependency");
            boolean changed = false;

            for (int i = 0; i < dependencies.getLength(); i++) {
                org.w3c.dom.Node node = dependencies.item(i);
                if (node.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                    continue;
                }
                Element dependency = (Element) node;
                String groupId = childText(dependency, "groupId");
                String artifactId = childText(dependency, "artifactId");
                if ("io.dropwizard".equals(groupId) && artifactId != null && artifactId.startsWith("dropwizard-") ) {
                    Element version = childElement(dependency, "version");
                    if (version != null && !"4.0.0".equals(version.getTextContent().trim())) {
                        version.setTextContent("4.0.0");
                        changed = true;
                    }
                }
            }

            if (changed) {
                Transformer transformer = TransformerFactory.newInstance().newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
                try (OutputStream out = Files.newOutputStream(pom.get())) {
                    transformer.transform(new DOMSource(document), new StreamResult(out));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to update pom", e);
        }
    }

    private static Optional<Path> findPom(Path start) {
        Path current = Files.isDirectory(start) ? start : start.getParent();
        while (current != null) {
            Path pom = current.resolve("pom.xml");
            if (Files.isRegularFile(pom)) {
                return Optional.of(pom);
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    private static String childText(Element parent, String tagName) {
        Element child = childElement(parent, tagName);
        return child == null ? null : child.getTextContent().trim();
    }

    private static Element childElement(Element parent, String tagName) {
        org.w3c.dom.NodeList nodes = parent.getElementsByTagName(tagName);
        for (int i = 0; i < nodes.getLength(); i++) {
            org.w3c.dom.Node node = nodes.item(i);
            if (node.getParentNode() == parent && node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                return (Element) node;
            }
        }
        return null;
    }
}
