package com.coreeng.supportbot.summary;

import com.coreeng.supportbot.ticket.TicketId;
import com.google.common.collect.ImmutableList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The inline citation markup the summary prompt asks the model to write: {@code [[phrase|123,456]]}
 * wraps the phrase of a claim and names the tickets whose {@code Reason} lines back it.
 *
 * <p>Markup rather than structured output so a mistake costs one citation, never the summary: a
 * tag that does not parse, or names no usable ticket, is reduced to its phrase and the prose reads
 * on. Nothing here throws.
 *
 * <p>Two stages. {@link #sanitise} runs once, on the model's answer, before it is stored: it keeps
 * only tickets the model was actually shown, so a snapshot never names a ticket outside its scope.
 * {@link #parse} runs on every read of the stored text.
 */
public final class SummaryCitations {

    /** Most tickets one claim keeps — the same "up to 5" the breakdown rows show. */
    static final int MAX_TICKETS_PER_CLAIM = 5;

    /** A well-formed tag: a phrase with no brackets or pipe, then a list of ticket ids. */
    private static final Pattern TAG = Pattern.compile("\\[\\[([^\\[\\]|]+)\\|([^\\[\\]|]*)]]");

    private static final Pattern TICKET_ID = Pattern.compile("#?(\\d{1,18})");

    /**
     * What is left of a tag that did not parse: an opening {@code [[}, or the {@code |ids]]} tail of
     * an outer tag around a nested one. Removed so the reader never sees raw markup.
     */
    private static final Pattern STRAY = Pattern.compile("\\[\\[|\\|[\\s#\\d,]*]]|]]");

    private SummaryCitations() {}

    /**
     * One run of the prose: plain text, or a cited phrase.
     *
     * @param ticketIds the tickets backing the phrase, in the model's order; empty for plain text
     */
    public record Span(String text, ImmutableList<TicketId> ticketIds) {

        public boolean cited() {
            return !ticketIds.isEmpty();
        }
    }

    /**
     * The model's answer with every citation checked against the tickets it was given.
     *
     * @param allowedTicketIds the tickets whose reasons were in the report
     * @return the same prose, with tags kept only where they name an allowed ticket (at most {@value
     *     #MAX_TICKETS_PER_CLAIM}, duplicates dropped) and everything else reduced to plain text
     */
    public static String sanitise(String raw, Set<TicketId> allowedTicketIds) {
        StringBuilder out = new StringBuilder(raw.length());
        for (Span span : parse(raw)) {
            ImmutableList<TicketId> kept = span.ticketIds().stream()
                    .filter(allowedTicketIds::contains)
                    .limit(MAX_TICKETS_PER_CLAIM)
                    .collect(ImmutableList.toImmutableList());
            if (kept.isEmpty()) {
                out.append(span.text());
            } else {
                out.append("[[").append(span.text()).append('|');
                out.append(String.join(
                        ",", kept.stream().map(id -> String.valueOf(id.id())).toList()));
                out.append("]]");
            }
        }
        return out.toString();
    }

    /**
     * Splits stored prose into its runs. Adjacent plain runs are merged, so prose with no citations
     * comes back as a single plain span.
     */
    public static ImmutableList<Span> parse(String text) {
        ImmutableList.Builder<Span> spans = ImmutableList.builder();
        StringBuilder plain = new StringBuilder();
        Matcher tag = TAG.matcher(text);
        int at = 0;
        while (tag.find()) {
            plain.append(stripStray(text.substring(at, tag.start())));
            at = tag.end();
            String phrase = tag.group(1);
            ImmutableList<TicketId> ids = ticketIds(tag.group(2));
            if (ids.isEmpty() || phrase.isBlank()) {
                plain.append(phrase);
                continue;
            }
            if (!plain.isEmpty()) {
                spans.add(new Span(plain.toString(), ImmutableList.of()));
                plain.setLength(0);
            }
            spans.add(new Span(phrase, ids));
        }
        plain.append(stripStray(text.substring(at)));
        if (!plain.isEmpty()) {
            spans.add(new Span(plain.toString(), ImmutableList.of()));
        }
        return spans.build();
    }

    /** The prose as a reader sees it: every tag replaced by its phrase. */
    public static String plainText(ImmutableList<Span> spans) {
        StringBuilder out = new StringBuilder();
        spans.forEach(span -> out.append(span.text()));
        return out.toString();
    }

    /** The distinct ids in a tag's list, in order; empty when any entry is not a ticket id. */
    private static ImmutableList<TicketId> ticketIds(String list) {
        Set<TicketId> ids = new LinkedHashSet<>();
        for (String entry : list.split(",", -1)) {
            Matcher id = TICKET_ID.matcher(entry.strip());
            if (!id.matches()) {
                return ImmutableList.of();
            }
            ids.add(new TicketId(Long.parseLong(id.group(1))));
        }
        return ImmutableList.copyOf(ids);
    }

    /**
     * Strips until nothing is left to strip: removing one fragment can join its neighbours into
     * another, even into a whole tag ({@code []][x|9]|1]]]} becomes {@code [[x|9]]}), and markup that
     * reached the stored text as plain text would come back as an unchecked citation on read. Each
     * pass shortens the text, so this ends.
     */
    private static String stripStray(String text) {
        String stripped = text;
        while (true) {
            String next = STRAY.matcher(stripped).replaceAll("");
            if (next.equals(stripped)) {
                return stripped;
            }
            stripped = next;
        }
    }
}
