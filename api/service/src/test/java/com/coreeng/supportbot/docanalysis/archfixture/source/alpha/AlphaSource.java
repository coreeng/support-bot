package com.coreeng.supportbot.docanalysis.archfixture.source.alpha;

import com.coreeng.supportbot.docanalysis.archfixture.FixtureBoundary;
import com.coreeng.supportbot.docanalysis.archfixture.source.alpha.internal.AlphaIndex;

public class AlphaSource implements FixtureBoundary {
    private final AlphaIndex index = new AlphaIndex();

    @Override
    public String answer() {
        return index.lookup();
    }
}
