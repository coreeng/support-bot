package com.coreeng.supportbot.prtracking.source;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Provider-neutral port for fetching pull/merge request data. Implementations adapt
 * a specific provider (GitHub, GitLab, ...) to this contract.
 *
 * <p>All methods throw {@link PrSourceException} for provider-side failures unless
 * documented otherwise.
 */
public interface PrSourceClient {

    /**
     * Identifies which provider this adapter speaks for. Used by {@link PrSourceClients} to
     * auto-register adapters by their provider — adapters that fail to register here will not
     * be reachable through {@code forProvider}.
     */
    Provider getProvider();

    /**
     * Fetches PR metadata including reviews and requested team reviewers. Adapters
     * are responsible for any provider-specific multi-call assembly.
     */
    PrMetadata fetchPullRequest(RepoCoord coord, int prNumber);

    /**
     * Fetches a file from the repository's default branch.
     *
     * @return the file content, or {@code null} if the file does not exist
     * @throws PrSourceException for all other failures (auth, server errors, network)
     */
    @Nullable String fetchFileContents(RepoCoord coord, String path);

    List<String> listChangedFiles(RepoCoord coord, int prNumber);

    /**
     * Resolves the members of a team/group on the provider. The interpretation of
     * {@code teamRef} is provider-defined: a team slug for GitHub, a group path for GitLab.
     */
    List<String> resolveTeamMembers(RepoCoord coord, String teamRef);
}
