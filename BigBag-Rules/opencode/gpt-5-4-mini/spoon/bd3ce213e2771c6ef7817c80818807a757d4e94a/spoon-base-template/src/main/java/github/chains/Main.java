package github.chains;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String JACKSON_GROUP_ID = "com.fasterxml.jackson.core";
    private static final String JACKSON_DATABIND = "jackson-databind";
    private static final String JACKSON_PREFIX = "jackson-";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-directory>");
        }

        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Source directory does not exist: " + root);
        }

        List<Path> pomFiles = collectPomFiles(root);
        int updatedFiles = 0;
        for (Path pom : pomFiles) {
            if (alignJacksonVersions(pom)) {
                updatedFiles++;
            }
        }

        System.out.println("Updated " + updatedFiles + " pom.xml file(s).");
    }

    private static List<Path> collectPomFiles(Path root) {
        List<Path> files = new ArrayList<>();
        collectPomFiles(root.toFile(), files);
        return files;
    }

    private static void collectPomFiles(File file, List<Path> files) {
        File[] children = file.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectPomFiles(child, files);
            } else if ("pom.xml".equals(child.getName())) {
                files.add(child.toPath());
            }
        }
    }

    private static boolean alignJacksonVersions(Path pomPath) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            Document document = factory.newDocumentBuilder().parse(pomPath.toFile());
            document.getDocumentElement().normalize();

            String databindVersion = findJacksonDatabindVersion(document);
            if (databindVersion == null) {
                return false;
            }

            boolean changed = false;
            NodeList dependencies = document.getElementsByTagName("dependency");
            for (int i = 0; i < dependencies.getLength(); i++) {
                Node node = dependencies.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element dependency = (Element) node;
                String groupId = childText(dependency, "groupId");
                String artifactId = childText(dependency, "artifactId");
                if (!JACKSON_GROUP_ID.equals(groupId) || artifactId == null || !artifactId.startsWith(JACKSON_PREFIX)) {
                    continue;
                }

                if (updateVersionIfNeeded(dependency, databindVersion)) {
                    changed = true;
                }
            }

            if (changed) {
                writeDocument(document, pomPath);
            }
            return changed;
        } catch (Exception e) {
            throw new RuntimeException("Failed to align Jackson versions in " + pomPath, e);
        }
    }

    private static String findJacksonDatabindVersion(Document document) {
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Node node = dependencies.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element dependency = (Element) node;
            if (JACKSON_GROUP_ID.equals(childText(dependency, "groupId")) && JACKSON_DATABIND.equals(childText(dependency, "artifactId"))) {
                return childText(dependency, "version");
            }
        }
        return null;
    }

    private static boolean updateVersionIfNeeded(Element dependency, String version) {
        Element versionElement = childElement(dependency, "version");
        if (versionElement == null) {
            return false;
        }
        String current = versionElement.getTextContent().trim();
        if (version.equals(current)) {
            return false;
        }
        versionElement.setTextContent(version);
        return true;
    }

    private static String childText(Element parent, String name) {
        Element element = childElement(parent, name);
        return element == null ? null : element.getTextContent().trim();
    }

    private static Element childElement(Element parent, String name) {
        NodeList children = parent.getElementsByTagName(name);
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getParentNode() == parent && node.getNodeType() == Node.ELEMENT_NODE) {
                return (Element) node;
            }
        }
        return null;
    }

    private static void writeDocument(Document document, Path pomPath) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        transformer.transform(new DOMSource(document), new StreamResult(pomPath.toFile()));
    }
}
