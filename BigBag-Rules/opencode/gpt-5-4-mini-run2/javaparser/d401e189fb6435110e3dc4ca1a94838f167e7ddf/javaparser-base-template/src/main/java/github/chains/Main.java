package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.ImportDeclaration;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class Main {
    private static final String LOGBACK_CLASSIC_GROUP = "ch.qos.logback";
    private static final String LOGBACK_CLASSIC_ARTIFACT = "logback-classic";
    private static final String SLF4J_GROUP = "org.slf4j";
    private static final String SLF4J_ARTIFACT = "slf4j-api";
    private static final String SLF4J_VERSION = "2.0.17";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path root = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile).forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void transformFile(Path path) {
        try {
            String name = path.getFileName().toString();
            if (name.endsWith(".java")) {
                transformJava(path);
            } else if ("pom.xml".equals(name)) {
                transformPom(path);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static void transformJava(Path path) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(path);
        if (cu.findAll(MethodCallExpr.class).stream().anyMatch(Main::isLogbackSetLevelCall)) {
            Files.write(path, cu.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static boolean isLogbackSetLevelCall(MethodCallExpr call) {
        if (!"setLevel".equals(call.getNameAsString()) || call.getArguments().size() != 1) {
            return false;
        }
        Expression scope = call.getScope().orElse(null);
        if (!(scope instanceof CastExpr)) {
            return false;
        }
        CastExpr cast = (CastExpr) scope;
        return "ch.qos.logback.classic.Logger".equals(cast.getType().asString())
                || "Logger".equals(cast.getType().asString());
    }

    private static void transformPom(Path path) throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(path.toFile());
        boolean hasLogback = hasDependency(document, LOGBACK_CLASSIC_GROUP, LOGBACK_CLASSIC_ARTIFACT);
        if (!hasLogback) {
            return;
        }

        boolean changed = updateDependencyVersion(document, SLF4J_GROUP, SLF4J_ARTIFACT, SLF4J_VERSION);
        if (!changed) {
            return;
        }

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        try (OutputStream os = Files.newOutputStream(path)) {
            transformer.transform(new DOMSource(document), new StreamResult(os));
        }
    }

    private static boolean hasDependency(Document document, String groupId, String artifactId) {
        NodeList deps = document.getElementsByTagName("dependency");
        for (int i = 0; i < deps.getLength(); i++) {
            org.w3c.dom.Node node = deps.item(i);
            if (node instanceof Element) {
                Element dep = (Element) node;
                if (groupId.equals(childText(dep, "groupId")) && artifactId.equals(childText(dep, "artifactId"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean updateDependencyVersion(Document document, String groupId, String artifactId, String version) {
        boolean changed = false;
        NodeList deps = document.getElementsByTagName("dependency");
        for (int i = 0; i < deps.getLength(); i++) {
            org.w3c.dom.Node node = deps.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element dep = (Element) node;
            if (!groupId.equals(childText(dep, "groupId")) || !artifactId.equals(childText(dep, "artifactId"))) {
                continue;
            }
            Element versionElement = childElement(dep, "version");
            if (versionElement == null) {
                versionElement = document.createElement("version");
                versionElement.setTextContent(version);
                dep.appendChild(versionElement);
                changed = true;
            } else if (!version.equals(versionElement.getTextContent().trim())) {
                versionElement.setTextContent(version);
                changed = true;
            }
        }
        return changed;
    }

    private static String childText(Element parent, String tagName) {
        Element child = childElement(parent, tagName);
        return child == null ? null : child.getTextContent().trim();
    }

    private static Element childElement(Element parent, String tagName) {
        NodeList children = parent.getElementsByTagName(tagName);
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node node = children.item(i);
            if (node.getParentNode() == parent && node instanceof Element) {
                return (Element) node;
            }
        }
        return null;
    }
}
