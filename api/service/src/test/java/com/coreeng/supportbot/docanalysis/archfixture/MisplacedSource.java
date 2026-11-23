package com.coreeng.supportbot.docanalysis.archfixture;

import com.coreeng.supportbot.docanalysis.DocumentationSource;
import com.coreeng.supportbot.docanalysis.OpenResult;
import com.coreeng.supportbot.docanalysis.SourceStatus;
import java.util.Optional;

public class MisplacedSource implements DocumentationSource {
    @Override
    public OpenResult open() {
        return new OpenResult.Unavailable("misplaced");
    }

    @Override
    public Optional<SourceStatus> status() {
        return Optional.empty();
    }

    @Override
    public String fingerprint() {
        return "misplaced";
    }
}
