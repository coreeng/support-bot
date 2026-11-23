package com.coreeng.supportbot.docanalysis;

/**
 * The one token estimate that the budget given to a source and the analysis's check of its answer
 * both use. Four characters a token is close for English prose and markdown; it is not a tokenizer.
 */
public final class TokenEstimator {

    private static final int CHARACTERS_PER_TOKEN = 4;

    private TokenEstimator() {}

    public static int estimate(CharSequence text) {
        return (text.length() + CHARACTERS_PER_TOKEN - 1) / CHARACTERS_PER_TOKEN;
    }
}
