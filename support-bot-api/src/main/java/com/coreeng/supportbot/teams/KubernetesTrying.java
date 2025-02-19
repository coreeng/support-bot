package com.coreeng.supportbot.teams;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.services.cloudidentity.v1.CloudIdentity;
import com.google.api.services.cloudidentity.v1.model.LookupGroupNameResponse;
import com.google.api.services.cloudidentity.v1.model.MemberRelation;
import com.google.api.services.cloudidentity.v1.model.SearchTransitiveMembershipsResponse;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableList;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceList;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.Subject;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.http.HttpClient;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.stream.Collectors.joining;

// This class is purely for testing purposes.
@SuppressWarnings("PMD")
class KubernetesTrying {
    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        GoogleCredentials creds = GoogleCredentials.getApplicationDefault()
//        GoogleCredentials creds = GoogleCredentials.fromStream(new FileInputStream("./support-bot-creds.json"))
            .createScoped("https://www.googleapis.com/auth/cloud-identity.groups.readonly");
        CloudIdentity cloudIdentity = new CloudIdentity.Builder(
            Utils.getDefaultTransport(),
            Utils.getDefaultJsonFactory(),
            new HttpCredentialsAdapter(creds)
        ).setApplicationName("Developer Platform")
            .build();

        Config config = Config.autoConfigure(null);
        KubernetesClientBuilder k8sClientBuilder = new KubernetesClientBuilder();
        k8sClientBuilder.withHttpClientBuilderConsumer(b -> {
                URI httpProxy = URI.create(config.getHttpProxy());
                b.proxyAddress(InetSocketAddress.createUnresolved(httpProxy.getHost(), httpProxy.getPort()))
                    .proxyType(HttpClient.ProxyType.HTTP);
            }
        );
        ImmutableList<String> rbPostfixes = ImmutableList.of("-admin", "-admin-viewer", "-viewer");
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        ExecutorCompletionService<Optional<TeamAndGroup>> completionService = new ExecutorCompletionService<>(executor);
        int futuresCount = 0;
        try (KubernetesClient k8sClient = k8sClientBuilder.build()) {
            try (executor) {
                NamespaceList nses = k8sClient.namespaces().list();
                for (Namespace ns : nses.getItems()) {
                    if (ns.getMetadata().getAnnotations().containsKey("cecg.io/description")) {
                        String nsName = ns.getMetadata().getName();
                        for (String rbPostfix : rbPostfixes) {
                            futuresCount += 1;
                            completionService.submit(() -> {
                                RoleBinding rb = k8sClient.rbac().roleBindings().inNamespace(nsName).withName(
                                    nsName + rbPostfix
                                ).get();
                                Subject saSubject = rb.getSubjects().stream()
                                    .filter(s -> "ServiceAccount".equals(s.getKind()))
                                    .findFirst()
                                    .orElse(null);
                                Subject groupSubject = rb.getSubjects().stream()
                                    .filter(s -> "Group".equals(s.getKind()))
                                    .findFirst()
                                    .orElse(null);
                                if (!(saSubject == null && groupSubject == null) && !(saSubject != null && groupSubject != null)) {
                                    System.out.println("!!! " + saSubject + " <-> " + groupSubject);
                                }
                                if (saSubject == null || groupSubject == null) {
                                    return Optional.empty();
                                }
                                return Optional.of(new TeamAndGroup(nsName, groupSubject.getName()));
                            });
                        }
                    }
                }
            }
        }
        // Team <-> Group
        //  N   <->  1
        // Group <-> User
        //  N    <->  N
        // Team <-> User
        //  N   <->  N
        // query: user -> team -> users

        Map<String, User> userByEmail = new HashMap<>();
        Map<String, Team> teamByName = new HashMap<>();
        Map<String, List<User>> groupIdToUsers = new HashMap<>();
        for (int i = 0; i < futuresCount; i++) {
            Optional<TeamAndGroup> opt = completionService.take().get();
            if (opt.isPresent()) {
                TeamAndGroup r = opt.get();
                Team team = teamByName.computeIfAbsent(r.team(), k -> new Team(
                    r.team(),
                    new HashSet<>(),
                    new HashSet<>()
                ));
                team.groupRefs().add(r.groupId());

                if (groupIdToUsers.containsKey(r.groupId())) {
                    List<User> users = groupIdToUsers.get(r.groupId());
                    team.users().addAll(users);
                    for (User user : users) {
                        user.teams().add(team);
                    }
                    continue;
                }

                LookupGroupNameResponse lookupResp = cloudIdentity.groups().lookup().setGroupKeyId(r.groupId()).execute();
                String groupId = lookupResp.getName();

                SearchTransitiveMembershipsResponse membershipResp = cloudIdentity.groups().memberships()
                    .searchTransitiveMemberships(groupId)
                    .execute();
                List<User> users = new ArrayList<>();
                for (MemberRelation m : membershipResp.getMemberships()) {
                    if (!m.getMember().startsWith("user")) {
                        continue;
                    }
                    String userEmail = m.getPreferredMemberKey().getFirst().getId();
                    User user = userByEmail.computeIfAbsent(userEmail, k -> new User(
                        userEmail,
                        new HashSet<>()
                    ));
                    users.add(user);
                }

                groupIdToUsers.put(r.groupId(), users);
                for (User user : users) {
                    team.users().add(user);
                    user.teams().add(team);
                }
            }
        }

        System.out.println("Teams:");
        for (Team team : teamByName.values()) {
            System.out.println("Name: " + team.name());
            System.out.println("GroupId: " + team.groupRefs().stream()
                .collect(joining(",", "[", "]"))
            );
            System.out.println("Users: " + team.users().stream()
                .map(User::email)
                .collect(joining(", ", "[", "]"))
            );
            System.out.println("\n---\n");
        }

        System.out.println("\n\nUsers:");
        for (User user : userByEmail.values()) {
            System.out.println("Email: " + user.email());
            System.out.println("Teams: " + user.teams().stream()
                .map(Team::name)
                .collect(joining(", ", "[", "]"))
            );
            System.out.println("\n---\n");
        }

        System.out.println("\n\nGrous:");
        for (var entry : groupIdToUsers.entrySet()) {
            String groupId = entry.getKey();
            List<User> users = entry.getValue();
            System.out.println("GroupId: " + groupId);
            System.out.println("Users: " + users.stream()
                .map(User::email)
                .collect(joining(", ", "[", "]"))
            );
        }

        System.out.printf(
            "Finished fetching teams info. Teams(%s), Groups(%s), Users(%s)%n",
            teamByName.size(),
            groupIdToUsers.size(),
            userByEmail.size()
        );
    }

    private record TeamAndGroup(
        String team,
        String groupId
    ) {
    }

    @Getter
    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    private static class User {
        @EqualsAndHashCode.Include
        private final String email;
        private final Set<Team> teams;
    }

    @Getter
    @EqualsAndHashCode
    @RequiredArgsConstructor
    private static class Team {
        @EqualsAndHashCode.Include
        private final String name;
        private final Set<String> groupRefs;
        private final Set<User> users;
    }
}
