package com.coreeng.supportbot.summarydata;

import com.coreeng.supportbot.config.SummaryAreaProps;
import com.google.common.collect.ImmutableList;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The deployment's sanitisation patterns: each match is removed unless it is one of the exceptions,
 * ignoring case, and whitespace is then collapsed to single spaces.
 */
@Component
@Slf4j
public class ThreadSanitiser {

    private final ImmutableList<Pattern> patterns;
    private final Set<String> exceptions;

    public ThreadSanitiser(SummaryAreaProps props) {
        var sanitisation = props.sanitisation();
        this.patterns = sanitisation.patterns().stream()
                .map(ThreadSanitiser::compilePattern)
                .collect(ImmutableList.toImmutableList());
        this.exceptions = sanitisation.exceptions().stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        log.info("Loaded {} sanitisation patterns and {} exceptions", patterns.size(), exceptions.size());
    }

    private static Pattern compilePattern(String regex) {
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException(
                    "Invalid sanitisation pattern: \"" + regex + "\" — " + e.getDescription(), e);
        }
    }

    public String sanitise(String text) {
        for (Pattern pattern : patterns) {
            text = pattern.matcher(text).replaceAll(match -> {
                if (exceptions.contains(match.group().toLowerCase(Locale.ROOT))) {
                    return match.group();
                }
                return "";
            });
        }
        return text.replaceAll("\\s+", " ").trim();
    }
}
