package com.coreeng.supportbot.docanalysis;

/**
 * The one token estimate that the budget given to a source and the analysis's check of its answer
 * both use: 3.5 characters a token, rounded up, the cautious end of the 3.4 to 4.0 measured on the
 * documentation this feature was sized against. It is not a tokenizer.
 */
public final class TokenEstimator {

    private TokenEstimator() {}

    public static int estimate(CharSequence text) {
        return Math.toIntExact((2L * text.length() + 6) / 7);
    }
}
