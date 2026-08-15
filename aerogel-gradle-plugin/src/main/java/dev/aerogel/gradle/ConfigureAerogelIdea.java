package dev.aerogel.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Adds Aerogel's reflective entry points to IntelliJ's shared project metadata. */
public abstract class ConfigureAerogelIdea extends DefaultTask {
    private static final String EVENT_HANDLER = "dev.aerogel.api.event.EventHandler";

    @Input
    public abstract ListProperty<String> getEntrypoints();

    @OutputFile
    public abstract RegularFileProperty getProjectFile();

    @TaskAction
    public void configure() throws Exception {
        Path file = getProjectFile().get().getAsFile().toPath();
        Files.createDirectories(file.getParent());
        Document document = readOrCreate(file);
        Element project = document.getDocumentElement();
        Element manager = childWithAttribute(project, "component", "name", "EntryPointsManager");
        if (manager == null) {
            manager = document.createElement("component");
            manager.setAttribute("name", "EntryPointsManager");
            project.appendChild(manager);
        }

        Element entryPoints = directChild(manager, "entry_points");
        if (entryPoints == null) {
            entryPoints = document.createElement("entry_points");
            entryPoints.setAttribute("version", "2.0");
            manager.appendChild(entryPoints);
        }
        for (String entrypoint : getEntrypoints().get()) {
            addClassEntry(document, entryPoints, entrypoint);
        }

        Element annotations = directChild(manager, "list");
        if (annotations == null) {
            annotations = document.createElement("list");
            manager.appendChild(annotations);
        }
        addAnnotation(document, annotations, EVENT_HANDLER);
        reindexAnnotations(annotations);
        writeAtomically(document, file);
    }

    private static Document readOrCreate(Path file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        if (Files.isRegularFile(file)) return factory.newDocumentBuilder().parse(file.toFile());

        Document document = factory.newDocumentBuilder().newDocument();
        Element project = document.createElement("project");
        project.setAttribute("version", "4");
        document.appendChild(project);
        return document;
    }

    private static void addClassEntry(
        Document document,
        Element entryPoints,
        String className
    ) {
        for (Element entry : directChildren(entryPoints, "entry_point")) {
            if (entry.getAttribute("TYPE").equals("class")
                && entry.getAttribute("FQNAME").equals(className)) return;
        }
        Element entry = document.createElement("entry_point");
        entry.setAttribute("TYPE", "class");
        entry.setAttribute("FQNAME", className);
        entryPoints.appendChild(entry);
    }

    private static void addAnnotation(
        Document document,
        Element annotations,
        String annotation
    ) {
        for (Element item : directChildren(annotations, "item")) {
            if (item.getAttribute("itemvalue").equals(annotation)) return;
        }
        Element item = document.createElement("item");
        item.setAttribute("class", "java.lang.String");
        item.setAttribute("itemvalue", annotation);
        annotations.appendChild(item);
    }

    private static void reindexAnnotations(Element annotations) {
        List<Element> items = directChildren(annotations, "item");
        annotations.setAttribute("size", Integer.toString(items.size()));
        for (int index = 0; index < items.size(); index++) {
            items.get(index).setAttribute("index", Integer.toString(index));
        }
    }

    private static Element childWithAttribute(
        Element parent,
        String name,
        String attribute,
        String value
    ) {
        for (Element child : directChildren(parent, name)) {
            if (child.getAttribute(attribute).equals(value)) return child;
        }
        return null;
    }

    private static Element directChild(Element parent, String name) {
        List<Element> matches = directChildren(parent, name);
        return matches.isEmpty() ? null : matches.getFirst();
    }

    private static List<Element> directChildren(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && element.getTagName().equals(name)) {
                result.add(element);
            }
        }
        return result;
    }

    private static void writeAtomically(Document document, Path file) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        var transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(document), new StreamResult(output));

        Path temporary = Files.createTempFile(file.getParent(), "aerogel-idea-", ".xml");
        try {
            Files.write(temporary, output.toByteArray());
            try {
                Files.move(
                    temporary,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
