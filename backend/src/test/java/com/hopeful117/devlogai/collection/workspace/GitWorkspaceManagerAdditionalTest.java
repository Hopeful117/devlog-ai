package com.hopeful117.devlogai.collection.workspace;

import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.entity.SourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class GitWorkspaceManagerAdditionalTest {

    @Mock private GitCommandExecutor git;

    @Test
    void shouldThrowWhenSourceNotPersisted() {
        Source source = Source.builder().id(null).type(SourceType.GIT_REPOSITORY).build();
        var manager = new GitWorkspaceManager("/tmp/workspaces", git);

        assertThrows(IllegalArgumentException.class,
                () -> manager.synchronize(source, "main"));
    }

    @Test
    void shouldThrowWhenSourceInactive() {
        Source source = Source.builder().id(UUID.randomUUID())
                .type(SourceType.GIT_REPOSITORY).active(false).build();
        var manager = new GitWorkspaceManager("/tmp/workspaces", git);

        assertThrows(IllegalArgumentException.class,
                () -> manager.synchronize(source, "main"));
    }
}
