package com.coreeng.supportbot.docanalysis.source.github;

import com.coreeng.supportbot.docanalysis.ContextAnswer;
import com.coreeng.supportbot.docanalysis.DistilledQuestion;
import com.coreeng.supportbot.docanalysis.DocumentationPage;
import com.coreeng.supportbot.docanalysis.TokenEstimator;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The documentation as routing sees it: the root pages, which sit directly in the configured path
 * and go with every answer, the pages of each product area, and the index the routing call reads.
 */
final class PageIndex {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final Pattern FRONTMATTER = Pattern.compile(
            "\\A\\uFEFF?---[ \\t]*+\\R(.*?)^(?:---|\\.\\.\\.)[ \\t]*+$\\R?", Pattern.DOTALL | Pattern.MULTILINE);
    private static final Pattern HEADING = Pattern.compile(" {0,3}#{1,6}[ \\t]++(.*+)");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Comparator<DocumentationPage> BY_PATH = Comparator.comparing(DocumentationPage::id);

    private final List<DocumentationPage> rootPages;
    private final Map<String, List<DocumentationPage>> areas;
    private final String text;
    private final int unreadableFrontmatter;

    private PageIndex(
            List<DocumentationPage> rootPages,
            Map<String, List<DocumentationPage>> areas,
            String text,
            int unreadableFrontmatter) {
        this.rootPages = rootPages;
        this.areas = areas;
        this.text = text;
        this.unreadableFrontmatter = unreadableFrontmatter;
    }

    static PageIndex of(List<DocumentationPage> pages) {
        List<DocumentationPage> root = new ArrayList<>();
        Map<String, List<DocumentationPage>> areas = new TreeMap<>();
        for (DocumentationPage page : pages.stream().sorted(BY_PATH).toList()) {
            String area = page.area();
            if (area == null) {
                root.add(page);
            } else {
                areas.computeIfAbsent(area, name -> new ArrayList<>()).add(page);
            }
        }
        StringBuilder text = new StringBuilder();
        int unreadable = 0;
        for (Map.Entry<String, List<DocumentationPage>> area : areas.entrySet()) {
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append("## ").append(area.getKey()).append('\n');
            for (DocumentationPage page : area.getValue()) {
                Entry entry = Entry.of(page.text());
                if (!entry.readable()) {
                    unreadable++;
                }
                text.append(String.join(" | ", page.id(), entry.title(), entry.description())
                                .stripTrailing())
                        .append('\n');
            }
        }
        areas.replaceAll((name, areaPages) -> List.copyOf(areaPages));
        return new PageIndex(List.copyOf(root), areas, text.toString(), unreadable);
    }

    /** Every page of each area, a line {@code path | title | description}, under a heading naming the area. */
    String text() {
        return text;
    }

    Set<String> areaNames() {
        return areas.keySet();
    }

    /** Pages indexed by their first heading because their frontmatter could not be read. */
    int unreadableFrontmatter() {
        return unreadableFrontmatter;
    }

    int rootTokens() {
        return tokens(rootPages);
    }

    int areaTokens(String area) {
        return tokens(areas.getOrDefault(area, List.of()));
    }

    /**
     * The root pages, then the pages of {@code supplied} by area name and path, so that answers for
     * the same areas start with the same text.
     *
     * @param supplied areas of the index, most likely first
     */
    ContextAnswer.Pages answer(List<String> supplied, DistilledQuestion question) {
        List<DocumentationPage> pages = new ArrayList<>(rootPages);
        supplied.stream().sorted().forEach(area -> pages.addAll(areas.get(area)));
        List<String> notSupplied =
                areas.keySet().stream().filter(area -> !supplied.contains(area)).toList();
        return new ContextAnswer.Pages(pages, supplied, notSupplied, question);
    }

    private static int tokens(List<DocumentationPage> pages) {
        return pages.stream()
                .mapToInt(page -> TokenEstimator.estimate(page.text()))
                .sum();
    }

    /**
     * @param readable false when the page opens with frontmatter that is not valid YAML; its title
     *     then comes from its first heading
     */
    record Entry(String title, String description, boolean readable) {

        static Entry of(String text) {
            Matcher frontmatter = FRONTMATTER.matcher(text);
            if (!frontmatter.find()) {
                return new Entry(firstHeading(text), "", true);
            }
            String body = text.substring(frontmatter.end());
            JsonNode fields;
            try {
                fields = YAML.readTree(frontmatter.group(1));
            } catch (JacksonException e) {
                return new Entry(firstHeading(body), "", false);
            }
            String title = field(fields, "title");
            return new Entry(title.isEmpty() ? firstHeading(body) : title, field(fields, "description"), true);
        }

        private static String field(@Nullable JsonNode fields, String name) {
            JsonNode value = fields == null ? null : fields.get(name);
            return value != null && value.isValueNode() && !value.isNull() ? oneLine(value.asText()) : "";
        }

        private static String firstHeading(String text) {
            boolean inCode = false;
            for (String line : text.lines().toList()) {
                String trimmed = line.strip();
                if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                    inCode = !inCode;
                    continue;
                }
                Matcher heading = HEADING.matcher(line);
                if (!inCode && heading.matches()) {
                    String title = withoutClosingHashes(heading.group(1).strip());
                    if (!title.isEmpty()) {
                        return oneLine(title);
                    }
                }
            }
            return "";
        }

        private static String withoutClosingHashes(String heading) {
            int end = heading.length();
            while (end > 0 && heading.charAt(end - 1) == '#') {
                end--;
            }
            return end == 0 || Character.isWhitespace(heading.charAt(end - 1))
                    ? heading.substring(0, end).strip()
                    : heading;
        }

        private static String oneLine(String text) {
            return WHITESPACE.matcher(text).replaceAll(" ").strip();
        }
    }
}
