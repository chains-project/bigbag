package github.chains;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
public final class Main {

    private static final String HAMCREST_GROUP = "org.hamcrest";

    private static final String HAMCREST_CORE = "hamcrest-core";

    private static final String HAMCREST_LIBRARY = "hamcrest-library";

    private static final String HAMCREST = "hamcrest";

    private Main() {
        // Utility class.
    }

    public static void main(final String[] args) throws Exception {
        final Path root = Paths.get(args.length > 0 ? args[0] : ".")
            .toAbsolutePath()
            .normalize();
        final List<Path> poms = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(path -> path.getFileName() != null)
                .filter(path -> "pom.xml".equals(path.getFileName().toString()))
                .forEach(poms::add);
        }
        for (final Path pom : poms) {
            rewritePom(pom);
        }
    }

    private static void rewritePom(final Path pom) throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setIgnoringComments(true);
        factory.setExpandEntityReferences(false);
        final Document doc;
        try (InputStream input = Files.newInputStream(pom)) {
            doc = factory.newDocumentBuilder().parse(input);
        }
        doc.getDocumentElement().normalize();
        final NodeList deps = doc.getElementsByTagName("dependency");
        final List<Element> matches = new ArrayList<>();
        Element hamcrestDependency = null;
        String version = null;
        String scope = null;
        boolean optional = false;
        for (int idx = 0; idx < deps.getLength(); idx++) {
            final Element dep = (Element) deps.item(idx);
            final String groupId = childText(dep, "groupId");
            final String artifactId = childText(dep, "artifactId");
            if (HAMCREST_GROUP.equals(groupId)) {
                if (HAMCREST.equals(artifactId)) {
                    hamcrestDependency = dep;
                }
                if (HAMCREST_CORE.equals(artifactId) || HAMCREST_LIBRARY.equals(artifactId)) {
                    matches.add(dep);
                    version = firstNonBlank(version, childText(dep, "version"));
                    scope = firstNonBlank(scope, childText(dep, "scope"));
                    optional = optional || Boolean.parseBoolean(childText(dep, "optional"));
                }
            }
        }
        if (matches.isEmpty()) {
            return;
        }
        for (final Element dep : matches) {
            dep.getParentNode().removeChild(dep);
        }
        final Element dependencies = firstChildElement(doc.getDocumentElement(), "dependencies")
            .orElseGet(() -> {
                final Element created = doc.createElement("dependencies");
                doc.getDocumentElement().appendChild(created);
                return created;
            });
        final Element newdep = hamcrestDependency == null
            ? doc.createElement("dependency")
            : hamcrestDependency;
        if (hamcrestDependency == null) {
            dependencies.appendChild(newdep);
        }
        setChildText(doc, newdep, "groupId", HAMCREST_GROUP);
        setChildText(doc, newdep, "artifactId", HAMCREST);
        if (version == null || version.isBlank()) {
            version = "2.2";
        }
        setChildText(doc, newdep, "version", version);
        if (scope != null && !scope.isBlank()) {
            setChildText(doc, newdep, "scope", scope);
        }
        if (optional) {
            setChildText(doc, newdep, "optional", "true");
        }
        normalizeWhitespace(dependencies);
        final var transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        Files.writeString(pom, xmlToString(doc, transformer), StandardCharsets.UTF_8);
    }

    private static String xmlToString(final Document doc, final javax.xml.transform.Transformer transformer)
        throws Exception {
        final java.io.StringWriter output = new java.io.StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(output));
        return output.toString();
    }

    private static Optional<Element> firstChildElement(final Element parent, final String name) {
        final NodeList children = parent.getElementsByTagName(name);
        for (int idx = 0; idx < children.getLength(); idx++) {
            final Node node = children.item(idx);
            if (node.getParentNode() == parent && node.getNodeType() == Node.ELEMENT_NODE) {
                return Optional.of((Element) node);
            }
        }
        return Optional.empty();
    }

    private static String childText(final Element parent, final String name) {
        return firstChildElement(parent, name)
            .map(Element::getTextContent)
            .map(String::trim)
            .orElse(null);
    }

    private static void setChildText(final Document doc, final Element parent, final String name, final String text) {
        firstChildElement(parent, name).ifPresentOrElse(
            child -> child.setTextContent(text),
            () -> {
                final Element child = doc.createElement(name);
                child.setTextContent(text);
                parent.appendChild(child);
            }
        );
    }

    private static String firstNonBlank(final String first, final String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private static void normalizeWhitespace(final Element parent) {
        final List<Node> children = new ArrayList<>();
        final NodeList list = parent.getChildNodes();
        for (int idx = 0; idx < list.getLength(); idx++) {
            children.add(list.item(idx));
        }
        for (final Node node : children) {
            if (node.getNodeType() == Node.TEXT_NODE && node.getTextContent().trim().isEmpty()) {
                parent.removeChild(node);
            }
        }
    }
}
