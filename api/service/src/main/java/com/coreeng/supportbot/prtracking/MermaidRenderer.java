package com.coreeng.supportbot.prtracking;

import com.coreeng.supportbot.dbschema.enums.PrTrackingStatus;
import com.coreeng.supportbot.prtracking.PrLifecycle.Transition;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the declarative {@link PrLifecycle#TRANSITIONS} table to a Mermaid {@code stateDiagram-v2}
 * — one edge ({@code from --> to : label}) per row. {@code from == null} ("any active state") rows
 * are expanded to one edge from each active state, in the order those states first appear in the
 * table. The committed diagram ({@code docs/diagrams/pr-lifecycle.generated.md}) is an optional,
 * best-effort artefact regenerated manually via {@code make regen-fsm-diagram} — nothing enforces it,
 * so refresh it after changing {@link PrLifecycle#TRANSITIONS}.
 */
public final class MermaidRenderer {

    private MermaidRenderer() {}

    public static String render(List<Transition> transitions) {
        List<PrTrackingStatus> activeStates = new ArrayList<>();
        for (Transition t : transitions) {
            PrTrackingStatus from = t.from();
            if (from != null && !activeStates.contains(from)) {
                activeStates.add(from);
            }
        }

        StringBuilder sb = new StringBuilder("stateDiagram-v2");
        for (Transition t : transitions) {
            PrTrackingStatus from = t.from();
            if (from == null) {
                for (PrTrackingStatus active : activeStates) {
                    appendEdge(sb, active, t.to(), t.label());
                }
            } else {
                appendEdge(sb, from, t.to(), t.label());
            }
        }
        return sb.toString();
    }

    private static void appendEdge(StringBuilder sb, PrTrackingStatus from, PrTrackingStatus to, String label) {
        sb.append("\n    ")
                .append(from.name())
                .append(" --> ")
                .append(to.name())
                .append(" : ")
                .append(label);
    }
}
