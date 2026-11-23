package com.coreeng.supportbot.docanalysis.archfixture.source.alpha.internal;

import com.coreeng.supportbot.docanalysis.ContextAnswer;
import com.coreeng.supportbot.docanalysis.DocumentationSession;

public class AlphaSession implements DocumentationSession {
    @Override
    public String version() {
        return "alpha";
    }

    @Override
    public ContextAnswer findContext(String thread, int budget) {
        return new ContextAnswer.Failed("alpha", true);
    }
}
