package com.coreeng.supportbot.docanalysis.archfixture.source;

import com.coreeng.supportbot.docanalysis.ContextAnswer;
import com.coreeng.supportbot.docanalysis.DocumentationSession;

public class RootSession implements DocumentationSession {
    @Override
    public String version() {
        return "root";
    }

    @Override
    public ContextAnswer findContext(String thread, int budget) {
        return new ContextAnswer.Failed("misplaced", false);
    }
}
