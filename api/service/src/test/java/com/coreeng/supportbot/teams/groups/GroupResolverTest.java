package com.coreeng.supportbot.teams.groups;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.teams.PlatformUsersFetcher;
import com.coreeng.supportbot.teams.PlatformUsersFetcher.Membership;
import java.util.List;
import org.junit.jupiter.api.Test;

class GroupResolverTest {

    @Test
    void dispatchesAzureRefToAzureFetcher() {
        FakeFetcher<GroupRef.Azure> azureFetcher =
                new FakeFetcher<>(ref -> List.of(new Membership("azure-" + ref.value() + "@x.com")));
        GroupResolver resolver = new GroupResolver(null, azureFetcher, null, null);

        List<Membership> result = resolver.resolveMembers(new GroupRef.Azure("6f0a1b27-1234-4abc-9def-abc123def456"));

        assertThat(result)
                .extracting(Membership::email)
                .containsExactly("azure-6f0a1b27-1234-4abc-9def-abc123def456@x.com");
    }

    @Test
    void dispatchesGoogleRefToGoogleFetcher() {
        FakeFetcher<GroupRef.Google> googleFetcher =
                new FakeFetcher<>(ref -> List.of(new Membership("google-" + ref.value())));
        GroupResolver resolver = new GroupResolver(googleFetcher, null, null, null);

        List<Membership> result = resolver.resolveMembers(new GroupRef.Google("eng@corp.com"));

        assertThat(result).extracting(Membership::email).containsExactly("google-eng@corp.com");
    }

    @Test
    void dispatchesStaticRefToStaticFetcher() {
        FakeFetcher<GroupRef.Static> staticFetcher =
                new FakeFetcher<>(ref -> List.of(new Membership("static-" + ref.value())));
        GroupResolver resolver = new GroupResolver(null, null, staticFetcher, null);

        List<Membership> result = resolver.resolveMembers(new GroupRef.Static("wow-group"));

        assertThat(result).extracting(Membership::email).containsExactly("static-wow-group");
    }

    @Test
    void dispatchesSlackRefToSlackFetcher() {
        FakeFetcher<GroupRef.Slack> slackFetcher =
                new FakeFetcher<>(ref -> List.of(new Membership("slack-" + ref.value() + "@x.com")));
        GroupResolver resolver = new GroupResolver(null, null, null, slackFetcher);

        List<Membership> result = resolver.resolveMembers(new GroupRef.Slack("S08948NBMED"));

        assertThat(result).extracting(Membership::email).containsExactly("slack-S08948NBMED@x.com");
    }

    @Test
    void jwtRefReturnsEmpty_doesNotInvokeAnyFetcher() {
        FakeFetcher<GroupRef.Static> staticFetcher = new FakeFetcher<>(ref -> {
            throw new AssertionError("Static fetcher should not be invoked for JWT ref");
        });
        GroupResolver resolver = new GroupResolver(null, null, staticFetcher, null);

        assertThat(resolver.resolveMembers(new GroupRef.Jwt("developers"))).isEmpty();
    }

    @Test
    void slackRefReturnsEmptyWhenNoSlackFetcherRegistered() {
        FakeFetcher<GroupRef.Static> staticFetcher = new FakeFetcher<>(ref -> List.of(new Membership("static@x.com")));
        GroupResolver resolver = new GroupResolver(null, null, staticFetcher, null);

        assertThat(resolver.resolveMembers(new GroupRef.Slack("S08948NBMED"))).isEmpty();
    }

    @Test
    void unregisteredProviderReturnsEmpty() {
        FakeFetcher<GroupRef.Static> staticFetcher = new FakeFetcher<>(ref -> List.of(new Membership("static@x.com")));
        GroupResolver resolver = new GroupResolver(null, null, staticFetcher, null);

        assertThat(resolver.resolveMembers(new GroupRef.Azure("6f0a1b27-1234-4abc-9def-abc123def456")))
                .isEmpty();
    }

    private static final class FakeFetcher<R extends GroupRef> implements PlatformUsersFetcher<R> {
        private final java.util.function.Function<R, List<Membership>> fn;

        FakeFetcher(java.util.function.Function<R, List<Membership>> fn) {
            this.fn = fn;
        }

        @Override
        public List<Membership> fetchMembershipsByGroupRef(R groupRef) {
            return fn.apply(groupRef);
        }
    }
}
