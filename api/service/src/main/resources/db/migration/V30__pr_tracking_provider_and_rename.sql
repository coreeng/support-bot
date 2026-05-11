-- Extend pr_tracking to multiple PR sources (GitHub today, GitLab next). The repo column rename
-- is the load-bearing part: every reference to PR_TRACKING.GITHUB_REPO in the codebase moves
-- with this migration in commit 2.
--
-- 'github' is set as a column default purely so existing rows back-fill on ALTER. The default is
-- dropped immediately afterwards so inserts must specify provider explicitly — that prevents a
-- future GitLab insert from silently landing under provider='github' if a code path forgets to
-- set it.
alter table pr_tracking
    add column provider text not null default 'github';

alter table pr_tracking
    rename column github_repo to repo;

-- The original unique was (ticket_id, github_repo, pr_number) — i.e. uniqueness ignored provider.
-- Now that provider exists, include it: the same ticket may legitimately reference the same
-- numeric ID across providers (e.g. GitHub PR #1 and GitLab MR !1 in similarly-named projects).
alter table pr_tracking
    drop constraint pr_tracking_ticket_repo_pr_unique;

alter table pr_tracking
    add constraint pr_tracking_ticket_provider_repo_pr_unique
        unique (ticket_id, provider, repo, pr_number);

alter table pr_tracking
    alter column provider drop default;

-- Supports the in-flight/insights queries that scope by provider+repo. The unique constraint above
-- covers (ticket_id, provider, repo, pr_number) but those four-column lookups don't help for
-- repo-scoped scans that don't have a ticket_id.
create index if not exists pr_tracking_provider_repo_idx
    on pr_tracking (provider, repo);
