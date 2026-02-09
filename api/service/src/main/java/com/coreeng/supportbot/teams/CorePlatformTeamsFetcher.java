package com.coreeng.supportbot.teams;

import com.google.common.collect.ImmutableList;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceList;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.Subject;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;

@Slf4j
@RequiredArgsConstructor
public class CorePlatformTeamsFetcher implements PlatformTeamsFetcher {
    private static final ImmutableList<String> roleBindingPostfixes = ImmutableList.of("-admin", "-admin-viewer", "-viewer");

    private final ExecutorService executor;
    private final KubernetesClient k8sClient;

    @Override
    public List<TeamAndGroupTuple> fetchTeams() {
        ExecutorCompletionService<Optional<TeamAndGroupTuple>> completionService = new ExecutorCompletionService<>(executor);
        int futuresCount = 0;
        NamespaceList nses = k8sClient.namespaces().list();
        for (Namespace ns : nses.getItems()) {
            // Tenant NS
            if (!ns.getMetadata().getAnnotations().containsKey("cecg.io/description")) {
                continue;
            }
            // System tenant, ignore
            if (ns.getMetadata().getLabels().containsKey("cecg-system.tree.hnc.x-k8s.io/depth")) {
                continue;
            }

            String nsName = ns.getMetadata().getName();
            for (String rbPostfix : roleBindingPostfixes) {
                futuresCount += 1;
                completionService.submit(() -> fetchTeamAndGroup(rbPostfix, nsName));
            }
        }
        List<TeamAndGroupTuple> result = new ArrayList<>();
        try {
            for (int i = 0; i < futuresCount; i++) {
                completionService.take().get().ifPresent(result::add);
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    @NonNull
    private Optional<TeamAndGroupTuple> fetchTeamAndGroup(String rbPostfix, String nsName) {
        String rbName = nsName + rbPostfix;
        RoleBinding rb = k8sClient.rbac().roleBindings().inNamespace(nsName).withName(
            rbName
        ).get();
        if (rb == null) {
            log.warn("Found namespaces({}) that look like tenant namespace, but missing rolebinding({})",
                nsName, rbName
            );
            return Optional.empty();
        }
        Subject saSubject = rb.getSubjects().stream()
            .filter(s -> "ServiceAccount".equals(s.getKind()))
            .findFirst()
            .orElse(null);
        Subject groupSubject = rb.getSubjects().stream()
            .filter(s -> "Group".equals(s.getKind()))
            .findFirst()
            .orElse(null);
        if (saSubject != null && groupSubject != null) {
            return Optional.of(new TeamAndGroupTuple(nsName, groupSubject.getName()));
        }
        return Optional.empty();
    }
}
