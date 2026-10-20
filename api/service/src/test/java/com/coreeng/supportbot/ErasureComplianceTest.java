package com.coreeng.supportbot;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.dbschema.DefaultSchema;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.TableOptions;
import org.junit.jupiter.api.Test;

class ErasureComplianceTest {
    private static final String COMPLIANCE_FILE = "/erasure-compliance.yaml";
    private static final String COMPLIANCE_PATH = "api/service/src/test/resources" + COMPLIANCE_FILE;
    private static final List<String> KINDS = List.of("anonymised", "reviewed");

    private final Map<String, Set<String>> tables = storedColumnsByTable();
    private final Map<String, Map<String, List<String>>> compliance = complianceList();

    @Test
    void everyTableIsOnTheComplianceList() {
        Set<String> unlisted = new TreeSet<>(tables.keySet());
        unlisted.removeAll(compliance.keySet());

        assertThat(unlisted)
                .withFailMessage("""
                        These tables are not on the erasure compliance list, so nothing says \
                        what Erasure Request processing does with the personal data they may hold:
                        %s

                        For each one, add an entry to %s listing every column under \
                        "anonymised" or "reviewed".
                        """, bulleted(unlisted), COMPLIANCE_PATH)
                .isEmpty();
    }

    @Test
    void everyListedTableStillExists() {
        Set<String> stale = new TreeSet<>(compliance.keySet());
        stale.removeAll(tables.keySet());

        assertThat(stale).withFailMessage("""
                        These entries on the erasure compliance list are no longer tables:
                        %s

                        Remove them from %s so the list only ever describes tables that exist.
                        """, bulleted(stale), COMPLIANCE_PATH).isEmpty();
    }

    @Test
    void everyColumnIsAccountedFor() {
        List<String> unaccounted = new ArrayList<>();
        tables.forEach((table, columns) -> {
            if (!compliance.containsKey(table)) {
                return;
            }
            Set<String> listed = listedColumns(table);
            columns.stream()
                    .filter(column -> !listed.contains(column))
                    .forEach(column -> unaccounted.add(table + "." + column));
        });

        assertThat(unaccounted)
                .withFailMessage("""
                        These columns are not accounted for on the erasure compliance list:
                        %s

                        A new column may carry personal data. Decide whether Erasure Request processing \
                        must rewrite it; if so list it under "anonymised", otherwise under "reviewed", \
                        in %s.
                        """, bulleted(unaccounted), COMPLIANCE_PATH)
                .isEmpty();
    }

    @Test
    void everyListedColumnStillExists() {
        List<String> stale = new ArrayList<>();
        compliance.keySet().forEach(table -> {
            Set<String> columns = tables.get(table);
            if (columns == null) {
                return;
            }
            listedColumns(table).stream()
                    .filter(column -> !columns.contains(column))
                    .forEach(column -> stale.add(table + "." + column));
        });

        assertThat(stale).withFailMessage("""
                        These columns on the erasure compliance list no longer exist on their table:
                        %s

                        Remove them from %s so the list only ever describes columns that exist.
                        """, bulleted(stale), COMPLIANCE_PATH).isEmpty();
    }

    @Test
    void noColumnIsListedUnderMoreThanOneKind() {
        List<String> repeated = new ArrayList<>();
        compliance.keySet().forEach(table -> {
            Set<String> seen = new TreeSet<>();
            KINDS.forEach(kind -> columns(table, kind).stream()
                    .filter(column -> !seen.add(column))
                    .forEach(column -> repeated.add(table + "." + column)));
        });

        assertThat(repeated)
                .withFailMessage("""
                        These columns are listed under more than one kind in %s:
                        %s

                        A column is rewritten or holds no personal data; list it once.
                        """, COMPLIANCE_PATH, bulleted(repeated))
                .isEmpty();
    }

    private Set<String> listedColumns(String table) {
        Set<String> listed = new TreeSet<>();
        KINDS.forEach(kind -> listed.addAll(columns(table, kind)));
        return listed;
    }

    private List<String> columns(String table, String kind) {
        Map<String, List<String>> entry = Objects.requireNonNullElse(compliance.get(table), Map.of());
        return Objects.requireNonNullElse(entry.get(kind), List.of());
    }

    /** Views are left out: they store nothing, and their columns come from tables the list covers. */
    private static Map<String, Set<String>> storedColumnsByTable() {
        Map<String, Set<String>> columnsByTable = new TreeMap<>();
        for (Table<?> table : DefaultSchema.DEFAULT_SCHEMA.getTables()) {
            if (table.getTableType() == TableOptions.TableType.VIEW) {
                continue;
            }
            columnsByTable.put(
                    table.getName(),
                    Arrays.stream(table.fields()).map(Field::getName).collect(Collectors.toCollection(TreeSet::new)));
        }
        return columnsByTable;
    }

    private static Map<String, Map<String, List<String>>> complianceList() {
        try (InputStream in = Objects.requireNonNull(
                ErasureComplianceTest.class.getResourceAsStream(COMPLIANCE_FILE), COMPLIANCE_FILE)) {
            return new ObjectMapper(new YAMLFactory()).readValue(in, new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String bulleted(Iterable<String> items) {
        List<String> lines = new ArrayList<>();
        items.forEach(item -> lines.add("  - " + item));
        return String.join("\n", lines);
    }
}
