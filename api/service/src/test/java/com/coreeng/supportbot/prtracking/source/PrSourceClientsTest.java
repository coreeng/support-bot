package com.coreeng.supportbot.prtracking.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class PrSourceClientsTest {

    private static final PrSourceClient GITHUB_CLIENT = new StubClient(Provider.GITHUB);
    private static final PrSourceClient GITLAB_CLIENT = new StubClient(Provider.GITLAB);

    @Test
    void forProviderReturnsTheMatchingClient() {
        PrSourceClients clients = new PrSourceClients(List.of(GITHUB_CLIENT, GITLAB_CLIENT));

        assertThat(clients.forProvider(Provider.GITHUB)).isSameAs(GITHUB_CLIENT);
        assertThat(clients.forProvider(Provider.GITLAB)).isSameAs(GITLAB_CLIENT);
    }

    @Test
    void forProviderThrowsPrSourceExceptionWhenProviderHasNoClient() {
        // A tracking row can outlive its provider's config (e.g. all GitLab repos removed, dropping
        // the bean, while active provider='gitlab' rows remain). This must surface as a recoverable
        // PrSourceException the poller can skip, not a hard error logged every cycle.
        PrSourceClients clients = new PrSourceClients(List.of(GITHUB_CLIENT));

        assertThatThrownBy(() -> clients.forProvider(Provider.GITLAB))
                .isInstanceOf(PrSourceException.class)
                .hasMessageContaining("GITLAB");
    }

    @Test
    void rejectsDuplicateProviderRegistration() {
        assertThatThrownBy(() -> new PrSourceClients(List.of(GITLAB_CLIENT, new StubClient(Provider.GITLAB))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GITLAB");
    }

    private record StubClient(Provider provider) implements PrSourceClient {
        @Override
        public Provider getProvider() {
            return provider;
        }

        @Override
        public PrMetadata fetchPullRequest(RepoCoord coord, int prNumber) {
            throw new UnsupportedOperationException();
        }

        @Override
        public @Nullable String fetchFileContents(RepoCoord coord, String path) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> listChangedFiles(RepoCoord coord, int prNumber) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> resolveTeamMembers(RepoCoord coord, String teamRef) {
            throw new UnsupportedOperationException();
        }
    }
}
