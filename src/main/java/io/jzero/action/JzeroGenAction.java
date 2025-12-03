package io.jzero.action;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import io.jzero.notification.Notification;
import io.jzero.runconfig.JzeroGenConfigurationFactory;
import io.jzero.runconfig.JzeroGenConfigurationType;
import io.jzero.runconfig.JzeroGenRunConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Action to execute jzero gen command
 */
public class JzeroGenAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (file == null) {
            return;
        }

        // Find the directory containing .jzero.yaml file
        String workingDir = findJzeroConfigDirectory(project, file);
        if (workingDir == null) {
            Notification.getInstance().error(project, "Cannot find .jzero.yaml file in parent directories");
            return;
        }

        executeJzeroGenCommand(project, "jzero gen", workingDir);
    }

    private void executeJzeroGenCommand(@NotNull Project project,
                                       @NotNull String command,
                                       @NotNull String workingDir) {
        // Create a temporary run configuration
        RunManager runManager = RunManager.getInstance(project);
        JzeroGenConfigurationType configurationType = new JzeroGenConfigurationType();
        JzeroGenConfigurationFactory factory =
            (JzeroGenConfigurationFactory) configurationType.getConfigurationFactories()[0];

        // Create run configuration
        JzeroGenRunConfiguration runConfiguration = new JzeroGenRunConfiguration(
            project, factory, "jzero gen"
        );
        runConfiguration.setCommand(command);
        runConfiguration.setWorkingDirectory(workingDir);

        // Create settings for the run configuration
        RunnerAndConfigurationSettings settings =
            runManager.createConfiguration(runConfiguration, factory);
        settings.setTemporary(true);

        try {
            // Execute the configuration using default run executor
            ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder
                .create(DefaultRunExecutor.getRunExecutorInstance(), settings);
            ExecutionManager.getInstance(project).restartRunProfile(builder.build());
        } catch (ExecutionException ex) {
            Notification.getInstance().error(project, "Failed to execute jzero gen command: " + ex.getMessage());
        }
    }

    @Nullable
    private String findJzeroConfigDirectory(@NotNull Project project, @NotNull VirtualFile startFile) {
        // Start from the current file/directory and search upward for .jzero.yaml
        VirtualFile currentDir = startFile.isDirectory() ? startFile : startFile.getParent();

        while (currentDir != null) {
            VirtualFile configFile = currentDir.findChild(".jzero.yaml");
            if (configFile != null && configFile.exists()) {
                return currentDir.getPath();
            }

            // Move up to parent directory
            currentDir = currentDir.getParent();

            // Prevent infinite loop by limiting the search depth (e.g., up to 10 levels)
            if (currentDir != null &&
                startFile.getPath().split("/").length - currentDir.getPath().split("/").length > 10) {
                break;
            }
        }

        return null;
    }
}