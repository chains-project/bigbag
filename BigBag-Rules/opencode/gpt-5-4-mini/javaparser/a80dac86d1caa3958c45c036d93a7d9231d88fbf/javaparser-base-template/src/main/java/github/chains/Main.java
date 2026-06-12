package github.chains;

import com.github.javaparser.StaticJavaParser;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class Main {
    private static final String LOGBACK_GROUP_ID = "ch.qos.logback";
    private static final String LOGBACK_ARTIFACT_ID = "logback-classic";
    private static final String LOGBACK_CORE_ARTIFACT_ID = "logback-core";
    private static final String SLF4J_GROUP_ID = "org.slf4j";
    private static final String TARGET_SLF4J_VERSION = "2.0.7";

    public static void main(String[] args) {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile).forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source tree: " + root, e);
        }
    }

    private static void transformFile(Path file) {
        String name = file.getFileName().toString();
        try {
            if ("pom.xml".equals(name)) {
                rewritePom(file);
            } else if (name.endsWith(".java")) {
                StaticJavaParser.parse(file);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to process " + file, e);
        }
    }

    private static void rewritePom(Path pomFile) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(false);

        Document document = factory.newDocumentBuilder().parse(pomFile.toFile());
        document.getDocumentElement().normalize();

        Map<String, String> properties = readProperties(document);
        boolean hasLogback14 = containsDependencyVersion(document, properties, LOGBACK_GROUP_ID, LOGBACK_ARTIFACT_ID, version -> version != null && version.startsWith("1.4."));

        if (!hasLogback14) {
            return;
        }

        boolean updated = updateSlf4jDependencies(document, properties);
        updated |= updateSlf4jProperties(document, properties);
        updated |= alignLogbackCore(document, properties);

        if (updated) {
            writeDocument(document, pomFile);
        }
    }

    private static Map<String, String> readProperties(Document document) {
        Map<String, String> properties = new HashMap<>();
        NodeList nodes = document.getElementsByTagName("properties");
        if (nodes.getLength() == 0) {
            return properties;
        }

        Element propertiesElement = (Element) nodes.item(0);
        NodeList children = propertiesElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                properties.put(child.getNodeName(), child.getTextContent().trim());
            }
        }
        return properties;
    }

    private static boolean containsDependencyVersion(Document document, Map<String, String> properties,
                                                     String groupId, String artifactId,
                                                     java.util.function.Predicate<String> versionPredicate) {
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Element dependency = (Element) dependencies.item(i);
            if (matchesDependency(dependency, groupId, artifactId)) {
                String version = resolveVersion(dependency, properties);
                if (versionPredicate.test(version)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean updateSlf4jDependencies(Document document, Map<String, String> properties) {
        boolean updated = false;
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Element dependency = (Element) dependencies.item(i);
            if (matchesGroup(dependency, SLF4J_GROUP_ID)) {
                String version = resolveVersion(dependency, properties);
                if (version != null && version.startsWith("1.7.")) {
                    setVersion(dependency, TARGET_SLF4J_VERSION);
                    updated = true;
                }
            }
        }
        return updated;
    }

    private static boolean updateSlf4jProperties(Document document, Map<String, String> properties) {
        boolean updated = false;
        Element propertiesElement = getFirstElement(document, "properties");
        if (propertiesElement == null) {
            return false;
        }

        NodeList children = propertiesElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                String name = child.getNodeName();
                String value = child.getTextContent().trim();
                if (name.toLowerCase().contains("slf4j") && value.startsWith("1.7.")) {
                    child.setTextContent(TARGET_SLF4J_VERSION);
                    updated = true;
                }
            }
        }
        return updated;
    }

    private static boolean alignLogbackCore(Document document, Map<String, String> properties) {
        Element dependenciesElement = getFirstElement(document, "dependencies");
        if (dependenciesElement == null) {
            return false;
        }

        String classicVersion = findDependencyVersion(document, properties, LOGBACK_GROUP_ID, LOGBACK_ARTIFACT_ID);
        if (classicVersion == null) {
            return false;
        }

        Element coreDependency = findDependencyElement(document, LOGBACK_GROUP_ID, LOGBACK_CORE_ARTIFACT_ID);
        if (coreDependency != null) {
            String currentVersion = resolveVersion(coreDependency, properties);
            if (!classicVersion.equals(currentVersion)) {
                setVersion(coreDependency, classicVersion);
                return true;
            }
            return false;
        }

        Element dependency = document.createElement("dependency");
        appendChildWithText(document, dependency, "groupId", LOGBACK_GROUP_ID);
        appendChildWithText(document, dependency, "artifactId", LOGBACK_CORE_ARTIFACT_ID);
        appendChildWithText(document, dependency, "version", classicVersion);
        dependenciesElement.appendChild(document.createTextNode("\n        "));
        dependenciesElement.appendChild(dependency);
        dependenciesElement.appendChild(document.createTextNode("\n    "));
        return true;
    }

    private static String findDependencyVersion(Document document, Map<String, String> properties, String groupId, String artifactId) {
        Element dependency = findDependencyElement(document, groupId, artifactId);
        return dependency == null ? null : resolveVersion(dependency, properties);
    }

    private static Element findDependencyElement(Document document, String groupId, String artifactId) {
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Element dependency = (Element) dependencies.item(i);
            if (matchesDependency(dependency, groupId, artifactId)) {
                return dependency;
            }
        }
        return null;
    }

    private static boolean matchesDependency(Element dependency, String groupId, String artifactId) {
        return Objects.equals(textOfChild(dependency, "groupId"), groupId)
                && Objects.equals(textOfChild(dependency, "artifactId"), artifactId);
    }

    private static boolean matchesGroup(Element dependency, String groupId) {
        return Objects.equals(textOfChild(dependency, "groupId"), groupId);
    }

    private static String resolveVersion(Element dependency, Map<String, String> properties) {
        String version = textOfChild(dependency, "version");
        if (version == null) {
            return null;
        }
        version = version.trim();
        if (version.startsWith("${") && version.endsWith("}")) {
            String propertyName = version.substring(2, version.length() - 1);
            return properties.get(propertyName);
        }
        return version;
    }

    private static void setVersion(Element dependency, String version) {
        Element versionElement = getFirstElement(dependency, "version");
        if (versionElement == null) {
            versionElement = dependency.getOwnerDocument().createElement("version");
            dependency.appendChild(versionElement);
        }
        versionElement.setTextContent(version);
    }

    private static void appendChildWithText(Document document, Element parent, String childName, String value) {
        parent.appendChild(document.createTextNode("\n            "));
        Element child = document.createElement(childName);
        child.setTextContent(value);
        parent.appendChild(child);
    }

    private static Element getFirstElement(Document document, String name) {
        return getFirstElement((Element) document.getDocumentElement(), name);
    }

    private static Element getFirstElement(Element parent, String name) {
        NodeList children = parent.getElementsByTagName(name);
        return children.getLength() == 0 ? null : (Element) children.item(0);
    }

    private static String textOfChild(Element parent, String childName) {
        Element child = getDirectChild(parent, childName);
        return child == null ? null : child.getTextContent().trim();
    }

    private static Element getDirectChild(Element parent, String childName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && childName.equals(child.getNodeName())) {
                return (Element) child;
            }
        }
        return null;
    }

    private static void writeDocument(Document document, Path file) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        transformer.transform(new DOMSource(document), new StreamResult(file.toFile()));
    }
}
