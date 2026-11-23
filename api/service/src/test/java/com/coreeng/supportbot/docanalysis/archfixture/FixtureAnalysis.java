package com.coreeng.supportbot.docanalysis.archfixture;

import com.coreeng.supportbot.docanalysis.archfixture.source.alpha.AlphaSource;

public class FixtureAnalysis {
    public String run(FixtureBoundary source) {
        if (source instanceof AlphaSource alpha) {
            return alpha.answer();
        }
        return source.answer();
    }
}
