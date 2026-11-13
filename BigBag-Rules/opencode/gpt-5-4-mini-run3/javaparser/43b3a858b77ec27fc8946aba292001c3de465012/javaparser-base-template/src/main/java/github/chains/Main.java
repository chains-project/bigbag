package github.chains;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class Main {
    private static final String TARGET_LOGBACK_GROUP = "ch.qos.logback";
    private static final String TARGET_LOGBACK_ARTIFACT = "logback-classic";
    private static final String TARGET_SLF4J_GROUP = "org.slf4j";
    private static final String UPDATED_SLF4J_VERSION = "2.0.17";
    private static final Pattern LOGBACK_14 = Pattern.compile("1\\.4\\..*");
    private static final Pattern SLF4J_17 = Pattern.compile("1\\.7\\..*");

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: Main <source-root>");
            System.exit(1);
        }

        final Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            System.err.println("Not a directory: " + root);
            System.exit(1);
        }

        try {
            final int modified = transformProject(root);
            System.out.println("Modified " + modified + " pom file(s).");
        } catch (IOException | ParserConfigurationException | TransformerException | SAXException e) {
            throw new RuntimeException("Failed to transform Maven project", e);
        }
    }

    private static int transformProject(Path root) throws IOException, ParserConfigurationException, TransformerException, SAXException {
        int modified = 0;
        final Deque<Path> stack = new ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            final Path current = stack.pop();
            if (Files.isDirectory(current)) {
                try (var stream = Files.list(current)) {
                    stream.forEach(stack::push);
                }
                continue;
            }

            if (current.getFileName() != null && "pom.xml".equals(current.getFileName().toString())) {
                if (rewritePom(current)) {
                    modified++;
                }
            }
        }

        return modified;
    }

    private static boolean rewritePom(Path pom) throws IOException, ParserConfigurationException, TransformerException, SAXException {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(true);

        final DocumentBuilder builder = factory.newDocumentBuilder();
        final Document document;
        try (InputStream in = Files.newInputStream(pom)) {
            document = builder.parse(in);
        }

        final boolean hasLogback14 = hasDependency(document, TARGET_LOGBACK_GROUP, TARGET_LOGBACK_ARTIFACT, LOGBACK_14);
        if (!hasLogback14) {
            return false;
        }

        final Set<String> slf4jPropertyNames = new HashSet<>();
        boolean changed = updateSlf4jDependencies(document);
        changed |= updateSlf4jProperties(document, slf4jPropertyNames);

        if (changed) {
            final TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            final Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            try (OutputStream out = Files.newOutputStream(pom, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                transformer.transform(new DOMSource(document), new StreamResult(out));
            }
        }

        return changed;
    }

    private static boolean hasDependency(Document document, String groupId, String artifactId, Pattern versionPattern) {
        final NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            final Node node = dependencies.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            final Element dependency = (Element) node;
            if (groupId.equals(textOfChild(dependency, "groupId"))
                    && artifactId.equals(textOfChild(dependency, "artifactId"))
                    && versionPattern.matcher(textOfChild(dependency, "version")).matches()) {
                return true;
            }
        }
        return false;
    }

    private static boolean updateSlf4jDependencies(Document document) {
        boolean changed = false;
        final NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            final Node node = dependencies.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            final Element dependency = (Element) node;
            if (!TARGET_SLF4J_GROUP.equals(textOfChild(dependency, "groupId"))) {
                continue;
            }

            final Element version = firstChildElement(dependency, "version");
            if (version != null && SLF4J_17.matcher(version.getTextContent().trim()).matches()) {
                version.setTextContent(UPDATED_SLF4J_VERSION);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean updateSlf4jProperties(Document document, Set<String> slf4jPropertyNames) {
        boolean changed = false;
        final Element properties = firstChildElement(document.getDocumentElement(), "properties");
        if (properties == null) {
            return false;
        }

        final NodeList children = properties.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            final Element property = (Element) node;
            final String name = property.getTagName();
            final String value = property.getTextContent().trim();
            if (name.toLowerCase().contains("slf4j") && SLF4J_17.matcher(value).matches()) {
                property.setTextContent(UPDATED_SLF4J_VERSION);
                slf4jPropertyNames.add(name);
                changed = true;
            }
        }
        return changed;
    }

    private static String textOfChild(Element parent, String childName) {
        final Element child = firstChildElement(parent, childName);
        return child == null ? "" : child.getTextContent().trim();
    }

    private static Element firstChildElement(Element parent, String childName) {
        final NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && childName.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        return null;
    }
}
