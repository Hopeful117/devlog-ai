package com.hopeful117.devlogai.collection.workspace;

import java.nio.file.Path;
import java.util.List;

public interface GitCommandExecutor {

    String execute(Path workingDirectory, List<String> arguments);
}
