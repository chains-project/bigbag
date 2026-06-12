package github.chains;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Main {
    public static void main(String[] args) {
        final Path root = Paths.get(args.length > 0 ? args[0] : ".");
        try {
            transform(root);
        } catch (final IOException err) {
            throw new UncheckedIOException(err);
        }
    }

    private static void transform(final Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
                throws IOException {
                if (file.getFileName().toString().equals("pom.xml")) {
                    updatePom(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void updatePom(final Path pom) throws IOException {
        final Document doc;
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            doc = factory.newDocumentBuilder().parse(Files.newInputStream(pom));
        } catch (final Exception err) {
            throw new IOException(String.format("Failed to parse %s", pom), err);
        }
        final NodeList dependencies = doc.getElementsByTagName("dependency");
        final List<Element> matches = new ArrayList<>();
        for (int idx = 0; idx < dependencies.getLength(); idx += 1) {
            final Node node = dependencies.item(idx);
            if (node instanceof Element) {
                final Element dependency = (Element) node;
                if ("org.hamcrest".equals(childText(dependency, "groupId"))) {
                    final String artifact = childText(dependency, "artifactId");
                    if ("hamcrest-core".equals(artifact) || "hamcrest-library".equals(artifact)) {
                        matches.add(dependency);
                    }
                }
            }
        }
        if (matches.isEmpty()) {
            return;
        }
        final Element template = matches.get(0);
        final Element normalized = (Element) template.cloneNode(true);
        setChildText(normalized, "artifactId", "hamcrest");
        if (childText(normalized, "version").isEmpty()) {
            setChildText(normalized, "version", "2.2");
        }
        final Node parent = template.getParentNode();
        for (final Element dependency : matches) {
            dependency.getParentNode().removeChild(dependency);
        }
        parent.appendChild(doc.createTextNode("\n    "));
        parent.appendChild(normalized);
        parent.appendChild(doc.createTextNode("\n"));
        write(doc, pom);
    }

    private static void write(final Document doc, final Path pom) throws IOException {
        try {
            final var transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.transform(new DOMSource(doc), new StreamResult(Files.newOutputStream(pom)));
        } catch (final Exception err) {
            throw new IOException(String.format("Failed to write %s", pom), err);
        }
    }

    private static String childText(final Element parent, final String name) {
        final NodeList children = parent.getElementsByTagName(name);
        if (children.getLength() == 0) {
            return "";
        }
        return Optional.ofNullable(children.item(0).getTextContent()).orElse("").trim();
    }

    private static void setChildText(final Element parent, final String name, final String value) {
        final NodeList children = parent.getElementsByTagName(name);
        if (children.getLength() == 0) {
            final Element child = parent.getOwnerDocument().createElement(name);
            child.setTextContent(value);
            parent.appendChild(child);
        } else {
            children.item(0).setTextContent(value);
        }
    }
}
