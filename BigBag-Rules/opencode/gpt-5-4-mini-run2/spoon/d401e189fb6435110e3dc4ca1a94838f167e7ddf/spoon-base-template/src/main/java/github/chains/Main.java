package github.chains;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final String LOGBACK_GROUP = "ch.qos.logback";
    private static final String LOGBACK_ARTIFACT = "logback-classic";
    private static final String SLF4J_GROUP = "org.slf4j";
    private static final String SLF4J_ARTIFACT = "slf4j-api";
    private static final String FIXED_SLF4J_VERSION = "2.0.1";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected input source directory");
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        Path pom = findPom(sourceRoot);
        if (pom == null) {
            return;
        }

        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom.toFile());
        document.getDocumentElement().normalize();

        String logbackVersion = findDependencyVersion(document, LOGBACK_GROUP, LOGBACK_ARTIFACT);
        if (logbackVersion == null || compareVersion(logbackVersion, "1.4.0") < 0) {
            return;
        }

        boolean changed = updateDependencyVersion(document, SLF4J_GROUP, SLF4J_ARTIFACT, FIXED_SLF4J_VERSION);
        if (changed) {
            writeDocument(document, pom);
        }
    }

    private static Path findPom(Path root) throws IOException {
        if (Files.isRegularFile(root) && root.getFileName().toString().equals("pom.xml")) {
            return root;
        }
        Path pom = root.resolve("pom.xml");
        return Files.isRegularFile(pom) ? pom : null;
    }

    private static String findDependencyVersion(Document document, String groupId, String artifactId) {
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Node node = dependencies.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element dependency = (Element) node;
            if (groupId.equals(textOf(dependency, "groupId")) && artifactId.equals(textOf(dependency, "artifactId"))) {
                return textOf(dependency, "version");
            }
        }
        return null;
    }

    private static boolean updateDependencyVersion(Document document, String groupId, String artifactId, String version) {
        boolean changed = false;
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Node node = dependencies.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element dependency = (Element) node;
            if (groupId.equals(textOf(dependency, "groupId")) && artifactId.equals(textOf(dependency, "artifactId"))) {
                setTextChild(document, dependency, "version", version);
                changed = true;
            }
        }
        return changed;
    }

    private static String textOf(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        return nodes.item(0).getTextContent().trim();
    }

    private static void setTextChild(Document document, Element parent, String tagName, String value) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        Element child;
        if (nodes.getLength() == 0) {
            child = document.createElement(tagName);
            parent.appendChild(child);
        } else {
            child = (Element) nodes.item(0);
        }
        child.setTextContent(value);
    }

    private static int compareVersion(String left, String right) {
        String[] l = left.split("\\.");
        String[] r = right.split("\\.");
        int max = Math.max(l.length, r.length);
        for (int i = 0; i < max; i++) {
            int lv = i < l.length ? parseInt(l[i]) : 0;
            int rv = i < r.length ? parseInt(r[i]) : 0;
            if (lv != rv) {
                return Integer.compare(lv, rv);
            }
        }
        return 0;
    }

    private static int parseInt(String value) {
        StringBuilder digits = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
            } else {
                break;
            }
        }
        return digits.length() == 0 ? 0 : Integer.parseInt(digits.toString());
    }

    private static void writeDocument(Document document, Path path) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        transformer.transform(new DOMSource(document), new StreamResult(path.toFile()));
    }
}
