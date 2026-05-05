package com.coreeng.supportbot.teams.groups;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.teams.PlatformUsersFetcher;
import com.coreeng.supportbot.teams.PlatformUsersFetcher.Membership;
import java.util.List;
import org.junit.jupiter.api.Test;

class GroupResolverTest {

    @Test
    void dispatchesAzureRefToAzureFetcher() {
        FakeFetcher<GroupRef.Azure> azureFetcher = new FakeFetcher<>(
                GroupRef.Provider.AZURE, ref -> List.of(new Membership("azure-" + ref.value() + "@x.com")));
        GroupResolver resolver = new GroupResolver(null, azureFetcher, null);

        List<Membership> result =
                resolver.resolveMembers(new GroupRef.Azure("6f0a1b27-1234-4abc-9def-abc123def456"));

        assertThat(result).extracting(Membership::email)
                .containsExactly("azure-6f0a1b27-1234-4abc-9def-abc123def456@x.com");
    }

    @Test
    void dispatchesGoogleRefToGoogleFetcher() {
        FakeFetcher<GroupRef.Google> googleFetcher = new FakeFetcher<>(
                GroupRef.Provider.GOOGLE, ref -> List.of(new Membership("google-" + ref.value())));
        GroupResolver resolver = new GroupResolver(googleFetcher, null, null);

        List<Membership> result = resolver.resolveMembers(new GroupRef.Google("eng@corp.com"));

        assertThat(result).extracting(Membership::email).containsExactly("google-eng@corp.com");
    }

    @Test
    void dispatchesStaticRefToStaticFetcher() {
        FakeFetcher<GroupRef.Static> staticFetcher = new FakeFetcher<>(
                GroupRef.Provider.STATIC, ref -> List.of(new Membership("static-" + ref.value())));
        GroupResolver resolver = new GroupResolver(null, null, staticFetcher);

        List<Membership> result = resolver.resolveMembers(new GroupRef.Static("wow-group"));

        assertThat(result).extracting(Membership::email).containsExactly("static-wow-group");
    }

    @Test
    void jwtRefReturnsEmpty_doesNotInvokeAnyFetcher() {
        FakeFetcher<GroupRef.Static> staticFetcher = new FakeFetcher<>(GroupRef.Provider.STATIC, ref -> {
            throw new AssertionError("Static fetcher should not be invoked for JWT ref");
        });
        GroupResolver resolver = new GroupResolver(null, null, staticFetcher);

        assertThat(resolver.resolveMembers(new GroupRef.Jwt("developers"))).isEmpty();
    }

    @Test
    void slackRefReturnsEmpty_noPullSideFetcherForSlack() {
        FakeFetcher<GroupRef.Static> staticFetcher = new FakeFetcher<>(
                GroupRef.Provider.STATIC, ref -> List.of(new Membership("static@x.com")));
        GroupResolver resolver = new GroupResolver(null, null, staticFetcher);

        assertThat(resolver.resolveMembers(new GroupRef.Slack("S08948NBMED"))).isEmpty();
    }

    @Test
    void unregisteredProviderReturnsEmpty() {
        // Only static fetcher registered; an Azure ref hits a null slot.
        FakeFetcher<GroupRef.Static> staticFetcher = new FakeFetcher<>(
                GroupRef.Provider.STATIC, ref -> List.of(new Membership("static@x.com")));
        GroupResolver resolver = new GroupResolver(null, null, staticFetcher);

        assertThat(resolver.resolveMembers(new GroupRef.Azure("6f0a1b27-1234-4abc-9def-abc123def456")))
                .isEmpty();
    }

    @Test
    void providerAvailable_reflectsRegisteredFetchers() {
        FakeFetcher<GroupRef.Static> staticFetcher = new FakeFetcher<>(GroupRef.Provider.STATIC, ref -> List.of());
        GroupResolver resolver = new GroupResolver(null, null, staticFetcher);

        assertThat(resolver.providerAvailable(GroupRef.Provider.STATIC)).isTrue();
        assertThat(resolver.providerAvailable(GroupRef.Provider.AZURE)).isFalse();
        assertThat(resolver.providerAvailable(GroupRef.Provider.GOOGLE)).isFalse();
        assertThat(resolver.providerAvailable(GroupRef.Provider.SLACK)).isFalse();
        assertThat(resolver.providerAvailable(GroupRef.Provider.JWT)).isFalse();
    }

    private static final class FakeFetcher<R extends GroupRef> implements PlatformUsersFetcher<R> {
        private final GroupRef.Provider provider;
        private final java.util.function.Function<R, List<Membership>> fn;

        FakeFetcher(GroupRef.Provider provider, java.util.function.Function<R, List<Membership>> fn) {
            this.provider = provider;
            this.fn = fn;
        }

        @Override
        public GroupRef.Provider provider() {
            return provider;
        }

        @Override
        public List<Membership> fetchMembershipsByGroupRef(R groupRef) {
            return fn.apply(groupRef);
        }
    }
}
