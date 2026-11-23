package com.coreeng.supportbot.docanalysis.archfixture.source.beta;

import com.coreeng.supportbot.docanalysis.archfixture.FixtureBoundary;
import com.coreeng.supportbot.docanalysis.archfixture.source.alpha.internal.AlphaIndex;

public class BetaSource implements FixtureBoundary {
    @Override
    public String answer() {
        return new AlphaIndex().lookup();
    }
}
