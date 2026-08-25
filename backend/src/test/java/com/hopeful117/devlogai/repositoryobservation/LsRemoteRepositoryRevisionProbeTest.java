package com.hopeful117.devlogai.repositoryobservation;

import com.hopeful117.devlogai.collection.workspace.GitCommandException;
import com.hopeful117.devlogai.collection.workspace.GitCommandExecutor;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.entity.SourceType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LsRemoteRepositoryRevisionProbeTest {

    private final GitCommandExecutor git = mock(GitCommandExecutor.class);
    private final LsRemoteRepositoryRevisionProbe probe =
            new LsRemoteRepositoryRevisionProbe(git, "target/probe-workspaces");

    @Test
    void shouldObserveHeadWithLsRemoteOnTheTrackedBranch() {
        Source source = gitSource("main");
        when(git.execute(any(), anyList())).thenReturn("B".repeat(40) + "\trefs/heads/main\n");

        String observed = probe.probeHead(source);

        assertThat(observed).isEqualTo("b".repeat(40));
        ArgumentCaptor<List<String>> command = ArgumentCaptor.forClass(List.class);
        verify(git).execute(any(Path.class), command.capture());
        assertThat(command.getValue()).containsExactly(
                "ls-remote", source.getRepositoryUrl(), "refs/heads/main");
    }

    @Test
    void shouldFallBackToSymbolicHeadWhenNoBranchIsConfigured() {
        Source source = gitSource(null);
        when(git.execute(any(), anyList())).thenReturn("a".repeat(40) + "\tHEAD\n");

        assertThat(probe.probeHead(source)).isEqualTo("a".repeat(40));
        ArgumentCaptor<List<String>> command = ArgumentCaptor.forClass(List.class);
        verify(git).execute(any(Path.class), command.capture());
        assertThat(command.getValue()).containsExactly(
                "ls-remote", source.getRepositoryUrl(), "HEAD");
    }

    @Test
    void shouldNeverInvokeWorkspaceMutatingGitOperations() {
        when(git.execute(any(), anyList())).thenReturn("a".repeat(40) + "\tHEAD\n");
        probe.probeHead(gitSource(null));
        ArgumentCaptor<List<String>> command = ArgumentCaptor.forClass(List.class);
        verify(git).execute(any(Path.class), command.capture());
        List<String> verbs = command.getValue();
        assertThat(verbs).anyMatch(argument -> argument.equals("ls-remote"));
        assertThat(verbs).noneMatch(argument ->
                List.of("fetch", "pull", "checkout", "reset", "clone", "clean")
                        .contains(argument));
    }

    @Test
    void shouldAccept64CharacterIdentities() {
        when(git.execute(any(), anyList()))
                .thenReturn("c".repeat(64) + "\trefs/heads/main\n");
        assertThat(probe.probeHead(gitSource("main"))).isEqualTo("c".repeat(64));
    }

    @Test
    void shouldFailExplicitlyWhenTheTrackedRefDoesNotExist() {
        when(git.execute(any(), anyList())).thenReturn("");
        assertThatThrownBy(() -> probe.probeHead(gitSource("gone-branch")))
                .isInstanceOf(GitCommandException.class)
                .hasMessageContaining("no revision");
    }

    private Source gitSource(String branch) {
        return Source.builder().id(java.util.UUID.randomUUID())
                .type(SourceType.GIT_REPOSITORY)
                .repositoryUrl("https://example.test/repository.git")
                .defaultBranch(branch).active(true).build();
    }
}
