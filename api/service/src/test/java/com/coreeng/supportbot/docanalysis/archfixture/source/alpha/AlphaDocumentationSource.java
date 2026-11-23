package com.coreeng.supportbot.docanalysis.archfixture.source.alpha;

import com.coreeng.supportbot.docanalysis.DocumentationSource;
import com.coreeng.supportbot.docanalysis.OpenResult;
import com.coreeng.supportbot.docanalysis.SourceStatus;
import java.util.Optional;

public class AlphaDocumentationSource implements DocumentationSource {
    @Override
    public OpenResult open() {
        return new OpenResult.Unavailable("alpha");
    }

    @Override
    public Optional<SourceStatus> status() {
        return Optional.empty();
    }

    @Override
    public String fingerprint() {
        return "alpha";
    }
}
