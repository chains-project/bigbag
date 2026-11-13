package github.chains;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Main {
    private static final String SLF4J_GROUP_ID = "org.slf4j";
    private static final String SLF4J_1_PREFIX = "1.7.";
    private static final String SLF4J_2_VERSION = "2.0.0";
    private static final String LOGBACK_GROUP_ID = "ch.qos.logback";
    private static final String LOGBACK_CLASSIC_ARTIFACT_ID = "logback-classic";
    private static final String LOGBACK_14_PREFIX = "1.4.";

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        int changed = 0;

        try (Stream<Path> paths = Files.walk(root)) {
            for (Path pom : paths.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().equals("pom.xml")).toList()) {
                if (transformPom(pom)) {
                    changed++;
                }
            }
        }

        System.out.println("Updated " + changed + " pom.xml file(s).");
    }

    private static boolean transformPom(Path pom) {
        try {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom.toFile());
            document.getDocumentElement().normalize();

            String logbackClassicVersion = findLogbackClassic14Version(document);
            if (logbackClassicVersion == null) {
                return false;
            }

            boolean changed = false;
            changed |= upgradeSlf4jDependencyVersions(document);
            changed |= upgradeSlf4jVersionProperties(document);
            changed |= alignLogbackCoreVersion(document, logbackClassicVersion);

            if (changed) {
                var transformer = TransformerFactory.newInstance().newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
                transformer.transform(new DOMSource(document), new StreamResult(pom.toFile()));
            }
            return changed;
        } catch (Exception e) {
            throw new RuntimeException("Unable to transform " + pom, e);
        }
    }

    private static String findLogbackClassic14Version(Document document) {
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Node node = dependencies.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element dependency = (Element) node;
            if (LOGBACK_GROUP_ID.equals(childText(dependency, "groupId"))
                    && LOGBACK_CLASSIC_ARTIFACT_ID.equals(childText(dependency, "artifactId"))) {
                String version = childText(dependency, "version");
                if (startsWith(version, LOGBACK_14_PREFIX)) {
                    return version.trim();
                }
            }
        }
        return null;
    }

    private static boolean upgradeSlf4jDependencyVersions(Document document) {
        boolean changed = false;
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Node node = dependencies.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element dependency = (Element) node;
            if (!SLF4J_GROUP_ID.equals(childText(dependency, "groupId"))) {
                continue;
            }

            Element version = childElement(dependency, "version");
            if (version != null && startsWith(version.getTextContent(), SLF4J_1_PREFIX)) {
                version.setTextContent(SLF4J_2_VERSION);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean upgradeSlf4jVersionProperties(Document document) {
        boolean changed = false;
        NodeList propertiesNodes = document.getElementsByTagName("properties");
        for (int i = 0; i < propertiesNodes.getLength(); i++) {
            Node node = propertiesNodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element properties = (Element) node;
            List<Element> candidates = new ArrayList<>();
            candidates.add(childElement(properties, "slf4j.version"));
            candidates.add(childElement(properties, "slf4j-api.version"));
            candidates.add(childElement(properties, "org.slf4j.version"));

            for (Element candidate : candidates) {
                if (candidate != null && startsWith(candidate.getTextContent(), SLF4J_1_PREFIX)) {
                    candidate.setTextContent(SLF4J_2_VERSION);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static boolean alignLogbackCoreVersion(Document document, String version) {
        boolean changed = false;
        NodeList dependencies = document.getElementsByTagName("dependency");
        Element existing = null;

        for (int i = 0; i < dependencies.getLength(); i++) {
            Node node = dependencies.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element dependency = (Element) node;
            if (LOGBACK_GROUP_ID.equals(childText(dependency, "groupId"))
                    && "logback-core".equals(childText(dependency, "artifactId"))) {
                existing = dependency;
                break;
            }
        }

        if (existing == null) {
            Element dependenciesElement = firstChildElement(document.getDocumentElement(), "dependencies");
            if (dependenciesElement == null) {
                return false;
            }
            existing = document.createElement("dependency");
            appendTextElement(document, existing, "groupId", LOGBACK_GROUP_ID);
            appendTextElement(document, existing, "artifactId", "logback-core");
            appendTextElement(document, existing, "version", version);
            dependenciesElement.appendChild(existing);
            return true;
        }

        Element existingVersion = childElement(existing, "version");
        if (existingVersion == null) {
            appendTextElement(document, existing, "version", version);
            return true;
        }
        if (!version.equals(existingVersion.getTextContent().trim())) {
            existingVersion.setTextContent(version);
            changed = true;
        }
        return changed;
    }

    private static Element firstChildElement(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && name.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        return null;
    }

    private static void appendTextElement(Document document, Element parent, String name, String value) {
        Element child = document.createElement(name);
        child.setTextContent(value);
        parent.appendChild(child);
    }

    private static Element childElement(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && name.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        return null;
    }

    private static String childText(Element parent, String name) {
        Element child = childElement(parent, name);
        return child == null ? null : child.getTextContent().trim();
    }

    private static boolean startsWith(String text, String prefix) {
        return text != null && text.trim().startsWith(prefix);
    }
}
