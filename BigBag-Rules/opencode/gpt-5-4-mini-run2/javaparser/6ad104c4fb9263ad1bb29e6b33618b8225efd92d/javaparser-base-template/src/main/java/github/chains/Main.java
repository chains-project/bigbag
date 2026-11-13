package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class Main {

    private static final String OLD_PREFIX = "org.bouncycastle.crypto.tls";
    private static final String NEW_PREFIX = "org.bouncycastle.tls";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source root does not exist: " + sourceRoot);
        }

        try {
            List<Path> javaFiles = Files.walk(sourceRoot)
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());

            boolean tlsImportSeen = false;
            for (Path javaFile : javaFiles) {
                tlsImportSeen |= rewriteFile(javaFile);
            }

            if (tlsImportSeen) {
                addTlsDependencyIfNeeded(sourceRoot);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite sources", e);
        }
    }

    private static boolean rewriteFile(Path javaFile) throws IOException {
        CompilationUnit compilationUnit = StaticJavaParser.parse(javaFile);
        LexicalPreservingPrinter.setup(compilationUnit);

        boolean changed = false;

        for (ImportDeclaration importDeclaration : compilationUnit.getImports()) {
            String name = importDeclaration.getNameAsString();
            if (name.startsWith(OLD_PREFIX)) {
                importDeclaration.setName(StaticJavaParser.parseName(name.replace(OLD_PREFIX, NEW_PREFIX)));
                changed = true;
            }
        }

        changed |= rewriteQualifiedNames(compilationUnit);

        if (changed) {
            Files.writeString(javaFile, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
        }

        return changed;
    }

    private static void addTlsDependencyIfNeeded(Path sourceRoot) throws IOException {
        Path pomPath = findPom(sourceRoot);
        if (pomPath == null) {
            return;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            Document document = factory.newDocumentBuilder().parse(pomPath.toFile());

            if (hasDependency(document, "org.bouncycastle", "bctls-jdk15on")) {
                return;
            }

            String version = findDependencyVersion(document, "org.bouncycastle", "bcprov-jdk15on");
            if (version == null || version.isEmpty()) {
                version = findProperty(document, "bouncycastle.version");
            }
            if (version == null || version.isEmpty()) {
                version = "1.67";
            }

            Element deps = firstChildElement(document.getDocumentElement(), "dependencies");
            if (deps == null) {
                deps = document.createElement("dependencies");
                document.getDocumentElement().appendChild(deps);
            }

            Element dependency = document.createElement("dependency");
            appendTextElement(document, dependency, "groupId", "org.bouncycastle");
            appendTextElement(document, dependency, "artifactId", "bctls-jdk15on");
            appendTextElement(document, dependency, "version", version);
            deps.appendChild(dependency);

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            javax.xml.transform.Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.transform(new DOMSource(document), new StreamResult(pomPath.toFile()));
        } catch (Exception e) {
            throw new IOException("Failed to update pom.xml at " + pomPath, e);
        }
    }

    private static Path findPom(Path sourceRoot) throws IOException {
        Path current = sourceRoot;
        while (current != null) {
            Path pom = current.resolve("pom.xml");
            if (Files.isRegularFile(pom)) {
                return pom;
            }
            current = current.getParent();
        }
        return null;
    }

    private static boolean hasDependency(Document document, String groupId, String artifactId) {
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            org.w3c.dom.Node node = dependencies.item(i);
            if (node instanceof Element) {
                Element element = (Element) node;
                if (groupId.equals(textOf(element, "groupId")) && artifactId.equals(textOf(element, "artifactId"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String findDependencyVersion(Document document, String groupId, String artifactId) {
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            org.w3c.dom.Node node = dependencies.item(i);
            if (node instanceof Element) {
                Element element = (Element) node;
                if (groupId.equals(textOf(element, "groupId")) && artifactId.equals(textOf(element, "artifactId"))) {
                    return textOf(element, "version");
                }
            }
        }
        return null;
    }

    private static String findProperty(Document document, String propertyName) {
        NodeList propertiesList = document.getElementsByTagName("properties");
        if (propertiesList.getLength() == 0) {
            return null;
        }

        Element properties = (Element) propertiesList.item(0);
        return textOf(properties, propertyName);
    }

    private static Element firstChildElement(Element parent, String tagName) {
        NodeList children = parent.getElementsByTagName(tagName);
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node node = children.item(i);
            if (node.getParentNode() == parent && node instanceof Element) {
                return (Element) node;
            }
        }
        return null;
    }

    private static void appendTextElement(Document document, Element parent, String tagName, String text) {
        Element element = document.createElement(tagName);
        element.setTextContent(text);
        parent.appendChild(element);
    }

    private static String textOf(Element parent, String tagName) {
        NodeList children = parent.getElementsByTagName(tagName);
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node node = children.item(i);
            if (node.getParentNode() == parent) {
                return node.getTextContent().trim();
            }
        }
        return null;
    }

    private static boolean rewriteQualifiedNames(Node node) {
        boolean changed = false;

        for (ClassOrInterfaceType type : node.findAll(ClassOrInterfaceType.class)) {
            if (rewriteType(type)) {
                changed = true;
            }
        }

        return changed;
    }

    private static boolean rewriteType(ClassOrInterfaceType classType) {
        String value = classType.asString();
        if (value.startsWith(OLD_PREFIX)) {
            classType.replace(StaticJavaParser.parseClassOrInterfaceType(value.replace(OLD_PREFIX, NEW_PREFIX)));
            return true;
        }
        return false;
    }
}
