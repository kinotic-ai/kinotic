package org.kinotic.idl.internal.directory;

import org.kinotic.idl.api.annotations.McpTool;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts method Javadoc at compile time into a jar resource per type, so MCP tool descriptions can come
 * from the comments a service already carries. Every documented interface is captured — a generic CRUD base
 * only becomes tool-relevant when a subtype is swept, long after the base compiled — while classes are
 * captured only when a method carries {@link McpTool} (an implementation override). The resource holds one
 * {@code methodName=description} line per documented method, where the description is the Javadoc main
 * description with inline tags resolved and HTML stripped.
 * Created by Navíd Mitchell 🤪 on 7/27/26.
 */
public class McpToolDocProcessor extends AbstractProcessor {

    /**
     * Classpath directory holding one {@code <binary-name>.properties} resource per extracted type.
     */
    public static final String DOCS_RESOURCE_DIRECTORY = "META-INF/kinotic/docs/";

    private static final Pattern INLINE_TAG = Pattern.compile("\\{@(\\w+)(?:\\s+([^}]*))?}");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of("*");
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getRootElements()) {
            if (element instanceof TypeElement typeElement) {
                try {
                    processType(typeElement);
                } catch (Exception e) {
                    // extraction is best effort; a failed type falls back to name-derived descriptions
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                                                             "Failed to extract Javadoc descriptions: " + e, typeElement);
                }
            }
        }
        return false;
    }

    private void processType(TypeElement typeElement) throws IOException {
        boolean isInterface = typeElement.getKind() == ElementKind.INTERFACE;
        Map<String, String> docs = new TreeMap<>();
        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.METHOD
                    && (isInterface || enclosed.getAnnotation(McpTool.class) != null)) {
                String docComment = processingEnv.getElementUtils().getDocComment(enclosed);
                if (docComment != null) {
                    String description = mainDescription(docComment);
                    if (!description.isEmpty()) {
                        // overloads collapse to one entry, matching IdlUtil.serviceFunctions
                        docs.put(((ExecutableElement) enclosed).getSimpleName().toString(), description);
                    }
                }
            }
        }
        if (!docs.isEmpty()) {
            write(typeElement, docs);
        }
    }

    /**
     * Returns the Javadoc main description: the text before the first block tag, with inline tags resolved
     * to their content, HTML removed, and whitespace collapsed.
     */
    private static String mainDescription(String docComment) {
        StringBuilder text = new StringBuilder();
        for (String line : docComment.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("@")) {
                break;
            }
            text.append(trimmed).append(' ');
        }
        String ret = resolveInlineTags(text.toString());
        ret = HTML_TAG.matcher(ret).replaceAll(" ");
        ret = ret.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ");
        return ret.replaceAll("\\s+", " ").trim();
    }

    private static String resolveInlineTags(String text) {
        Matcher matcher = INLINE_TAG.matcher(text);
        StringBuilder ret = new StringBuilder();
        while (matcher.find()) {
            String tag = matcher.group(1);
            String content = matcher.group(2) == null ? "" : matcher.group(2).trim();
            String replacement;
            if (tag.equals("link") || tag.equals("linkplain")) {
                replacement = linkText(content);
            } else if (tag.equals("code") || tag.equals("literal") || tag.equals("value")) {
                replacement = content;
            } else {
                // inheritDoc, docRoot, and unknown tags carry no readable text
                replacement = "";
            }
            matcher.appendReplacement(ret, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(ret);
        return ret.toString();
    }

    // "package.Class#member label" -> "label"; "package.Class#member" -> "member"; "package.Class" -> "Class"
    private static String linkText(String content) {
        String ret;
        int labelStart = content.indexOf(' ');
        if (labelStart >= 0) {
            ret = content.substring(labelStart + 1);
        } else {
            String reference = content;
            int member = reference.indexOf('#');
            if (member >= 0) {
                ret = reference.substring(member + 1);
            } else {
                ret = reference.substring(reference.lastIndexOf('.') + 1);
            }
        }
        return ret;
    }

    private void write(TypeElement typeElement, Map<String, String> docs) throws IOException {
        String binaryName = processingEnv.getElementUtils().getBinaryName(typeElement).toString();
        // written by hand instead of Properties.store, whose timestamp comment breaks reproducible jars
        try (Writer writer = processingEnv.getFiler()
                                          .createResource(StandardLocation.CLASS_OUTPUT,
                                                          "",
                                                          DOCS_RESOURCE_DIRECTORY + binaryName + ".properties",
                                                          typeElement)
                                          .openWriter()) {
            for (Map.Entry<String, String> doc : docs.entrySet()) {
                writer.write(doc.getKey());
                writer.write('=');
                writer.write(doc.getValue().replace("\\", "\\\\"));
                writer.write('\n');
            }
        }
    }

}
