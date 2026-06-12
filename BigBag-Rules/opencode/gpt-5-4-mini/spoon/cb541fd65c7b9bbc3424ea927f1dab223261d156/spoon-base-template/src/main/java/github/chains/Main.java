package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
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
    private static final String HAMCREST_ARTIFACT = "hamcrest";
    private static final String[] OLD_ARTIFACTS = {"hamcrest-core", "hamcrest-library"};
    private static final Pattern ITERABLE_MATCHER = Pattern.compile(
        "Matcher<Iterable<\\? extends ([^>]+)>>"
    );
    private static final Pattern COLLECTION_MATCHER = Pattern.compile(
        "Matcher<Collection<\\? extends ([^>]+)>>"
    );
    private static final Pattern PLAIN_ITERABLE_MATCHER = Pattern.compile(
        "Matcher<Iterable<([A-Za-z0-9_$.]+)>>"
    );
    private static final Pattern PLAIN_COLLECTION_MATCHER = Pattern.compile(
        "Matcher<Collection<([A-Za-z0-9_$.]+)>>"
    );

    private Main() {
        // utility class
    }

    public static void main(final String[] args) throws Exception {
        final Path root = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        transform(root);
    }

    public static void transform(final Path root) throws Exception {
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
                throws IOException {
                if (Objects.equals(file.getFileName().toString(), "pom.xml")) {
                    try {
                        rewritePom(file);
                    } catch (final Exception err) {
                        throw new IOException(err);
                    }
                } else if (file.getFileName().toString().endsWith(".java")) {
                    try {
                        rewriteJava(file);
                    } catch (final Exception err) {
                        throw new IOException(err);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void rewriteJava(final Path file) throws IOException {
        final String original = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        String updated = ITERABLE_MATCHER.matcher(original).replaceAll("Matcher<? extends Iterable<? extends $1>>");
        updated = COLLECTION_MATCHER.matcher(updated).replaceAll("Matcher<? extends Collection<? extends $1>>");
        updated = PLAIN_ITERABLE_MATCHER.matcher(updated).replaceAll("Matcher<? extends Iterable<? extends $1>>");
        updated = PLAIN_COLLECTION_MATCHER.matcher(updated).replaceAll("Matcher<? extends Collection<? extends $1>>");
        updated = updated.replace("Matcher<Iterable<? extends String>>", "Matcher<? extends Iterable<? extends String>>");
        updated = updated.replace("Matcher<Collection<? extends String>>", "Matcher<? extends Collection<? extends String>>");
        if (!original.equals(updated)) {
            Files.write(file, updated.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void rewritePom(final Path pom) throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        final Document doc = factory.newDocumentBuilder().parse(pom.toFile());
        final List<Element> blocks = new ArrayList<>();
        collectElementsByName(doc.getDocumentElement(), "dependencies", blocks);
        boolean changed = false;
        for (final Element deps : blocks) {
            changed |= rewriteDependenciesBlock(doc, deps);
        }
        if (changed) {
            final javax.xml.transform.Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.transform(new DOMSource(doc), new StreamResult(pom.toFile()));
        }
    }

    private static boolean rewriteDependenciesBlock(final Document doc, final Element deps) {
        final List<Element> matches = new ArrayList<>();
        Element existingHamcrest = null;
        final NodeList children = deps.getChildNodes();
        for (int idx = 0; idx < children.getLength(); idx++) {
            final Node node = children.item(idx);
            if (!(node instanceof Element)) {
                continue;
            }
            final Element dep = (Element) node;
            if (!"dependency".equals(local(dep))) {
                continue;
            }
            final String groupId = childText(dep, "groupId");
            final String artifactId = childText(dep, "artifactId");
            if (HAMCREST_GROUP.equals(groupId) && HAMCREST_ARTIFACT.equals(artifactId)) {
                existingHamcrest = dep;
            } else if (HAMCREST_GROUP.equals(groupId) && isOldArtifact(artifactId)) {
                matches.add(dep);
            }
        }
        if (matches.isEmpty()) {
            return false;
        }
        final Element template = matches.get(0);
        if (existingHamcrest == null) {
            existingHamcrest = doc.createElementNS(template.getNamespaceURI(), template.getTagName());
            setChildText(doc, existingHamcrest, "groupId", HAMCREST_GROUP);
            setChildText(doc, existingHamcrest, "artifactId", HAMCREST_ARTIFACT);
            final String version = childText(template, "version");
            if (version != null && !version.isEmpty()) {
                setChildText(doc, existingHamcrest, "version", version);
            }
            final String scope = childText(template, "scope");
            if (scope != null && !scope.isEmpty()) {
                setChildText(doc, existingHamcrest, "scope", scope);
            }
            final String optional = childText(template, "optional");
            if (optional != null && !optional.isEmpty()) {
                setChildText(doc, existingHamcrest, "optional", optional);
            }
            deps.insertBefore(existingHamcrest, matches.get(0));
        }
        for (final Element match : matches) {
            deps.removeChild(match);
        }
        return true;
    }

    private static void collectElementsByName(final Element root, final String name, final List<Element> out) {
        if (name.equals(local(root))) {
            out.add(root);
        }
        final NodeList children = root.getChildNodes();
        for (int idx = 0; idx < children.getLength(); idx++) {
            final Node node = children.item(idx);
            if (node instanceof Element) {
                collectElementsByName((Element) node, name, out);
            }
        }
    }

    private static boolean isOldArtifact(final String artifactId) {
        for (final String artifact : OLD_ARTIFACTS) {
            if (artifact.equals(artifactId)) {
                return true;
            }
        }
        return false;
    }

    private static String local(final Element element) {
        final String local = element.getLocalName();
        return local == null ? element.getTagName() : local;
    }

    private static String childText(final Element parent, final String name) {
        final NodeList children = parent.getChildNodes();
        for (int idx = 0; idx < children.getLength(); idx++) {
            final Node node = children.item(idx);
            if (node instanceof Element && name.equals(local((Element) node))) {
                return node.getTextContent().trim();
            }
        }
        return null;
    }

    private static void setChildText(final Document doc, final Element parent, final String name, final String value) {
        final Element child = doc.createElementNS(parent.getNamespaceURI(), name);
        child.setTextContent(value);
        parent.appendChild(child);
    }
}
