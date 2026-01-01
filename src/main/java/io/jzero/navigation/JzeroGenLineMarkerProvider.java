package io.jzero.navigation;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import io.jzero.runconfig.JzeroGenConfigurationFactory;
import io.jzero.runconfig.JzeroGenConfigurationType;
import io.jzero.runconfig.JzeroGenRunConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.List;

/**
 * LineMarker provider for jzero gen execution buttons
 * Supports .jzero.yaml, .api, .proto, and .sql files:
 * - For .jzero.yaml: Shows button next to "gen" and "zrpcclient" keywords
 * - For .api: Shows button on first line to execute "jzero gen --desc"
 * - For .proto: Shows button on first line to execute "jzero gen --desc" (only in desc/proto, excludes third_party)
 * - For .sql: Shows button on first line to execute "jzero gen --desc" (only in desc/sql)
 */
public class JzeroGenLineMarkerProvider implements LineMarkerProvider {

    @Nullable
    @Override
    public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        PsiFile containingFile = element.getContainingFile();
        if (containingFile == null) {
            return null;
        }

        VirtualFile virtualFile = containingFile.getVirtualFile();
        if (virtualFile == null) {
            return null;
        }

        Project project = containingFile.getProject();
        String fileName = virtualFile.getName();

        // Handle .jzero.yaml files (existing functionality)
        if (".jzero.yaml".equals(fileName)) {
            return createYamlLineMarker(element, project, virtualFile);
        }

        // Handle .api files (existing functionality)
        if (fileName.endsWith(".api")) {
            return createApiLineMarker(element, project, virtualFile);
        }

        // Handle .proto files (only in desc/proto, exclude third_party)
        if (fileName.endsWith(".proto")) {
            String filePath = virtualFile.getPath();
            if (filePath.contains("/desc/proto/") && !filePath.contains("/desc/proto/third_party/")) {
                return createApiLineMarker(element, project, virtualFile);
            }
        }

        // Handle .sql files (only in desc/sql directory)
        if (fileName.endsWith(".sql")) {
            String filePath = virtualFile.getPath();
            if (filePath.contains("/desc/sql/")) {
                return createApiLineMarker(element, project, virtualFile);
            }
        }

        return null;
    }

    @Nullable
    private LineMarkerInfo<?> createYamlLineMarker(@NotNull PsiElement element,
                                                  @NotNull Project project,
                                                  @NotNull VirtualFile virtualFile) {
        // Check if the element contains the "gen" or "zrpcclient" keyword
        String elementText = element.getText();
        if (elementText == null) {
            return null;
        }

        String trimmedText = elementText.trim();
        String command = null;
        String tooltip;

        if (trimmedText.equals("gen")) {
            command = "jzero gen";
            tooltip = "Execute jzero gen";
        } else if (trimmedText.equals("zrpcclient")) {
            command = "jzero gen zrpcclient";
            tooltip = "Execute jzero gen zrpcclient";
        } else {
            tooltip = null;
            return null;
        }

        String configDir = virtualFile.getParent() != null ? virtualFile.getParent().getPath() : "";

        String finalCommand = command;
        return new LineMarkerInfo<>(
                element,
                element.getTextRange(),
                AllIcons.Actions.Execute,
                psiElement -> tooltip,
                new GutterIconNavigationHandler<PsiElement>() {
                    @Override
                    public void navigate(MouseEvent e, PsiElement elt) {
                        executeJzeroGenCommand(project, virtualFile, finalCommand);
                    }
                },
                GutterIconRenderer.Alignment.LEFT
        );
    }

    @Nullable
    private LineMarkerInfo<?> createApiLineMarker(@NotNull PsiElement element,
                                                 @NotNull Project project,
                                                 @NotNull VirtualFile virtualFile) {
        PsiFile containingFile = element.getContainingFile();

        // Only show marker on the very first non-whitespace element of the API file
        PsiElement firstElement = containingFile.getFirstChild();
        while (firstElement != null &&
               (firstElement instanceof PsiWhiteSpace ||
                firstElement.getText().trim().isEmpty())) {
            firstElement = firstElement.getNextSibling();
        }

        // Only show the marker if this element is the first non-whitespace element
        if (firstElement == null || element != firstElement) {
            return null;
        }

        // Only show button if the file path contains /desc/ pattern
        String filePath = virtualFile.getPath();
        if (!filePath.contains("/desc/")) {
            return null;
        }

        // Don't calculate the command here - do it dynamically when clicked
        // to ensure we get the current file path after potential renames

        return new LineMarkerInfo<>(
                element,
                element.getTextRange(),
                AllIcons.Actions.Execute,
                psiElement -> "Execute jzero gen --desc",
                new GutterIconNavigationHandler<PsiElement>() {
                    @Override
                    public void navigate(MouseEvent e, PsiElement elt) {
                        executeApiGenCommand(project, elt);
                    }
                },
                GutterIconRenderer.Alignment.LEFT
        );
    }

    private void executeApiGenCommand(@NotNull Project project, @NotNull PsiElement element) {
        PsiFile containingFile = element.getContainingFile();
        if (containingFile == null) {
            return;
        }

        VirtualFile virtualFile = containingFile.getVirtualFile();
        if (virtualFile == null) {
            return;
        }

        // Force refresh to get the latest file information
        virtualFile.refresh(true, false);

        String apiFilePath = virtualFile.getPath();

        // For api/proto/sql files in desc directory, use the parent of desc as working directory
        // e.g., /path/to/desc/api/xx.api -> working dir is /path/to
        String workingDir = findDescBasedWorkingDirectory(apiFilePath);

        String relativePath = calculateRelativePath(workingDir, apiFilePath);
        String cmd = "jzero gen --desc " + relativePath;

        executeJzeroGenCommandWithWorkingDir(project, virtualFile, cmd, workingDir);
    }

    private void executeJzeroGenCommand(@NotNull Project project,
                                       @NotNull VirtualFile triggerFile,
                                       @NotNull String command) {
        // For .jzero.yaml files, find the directory containing .jzero.yaml file
        String workingDir = findJzeroConfigDirectory(project, triggerFile);
        if (workingDir == null) {
            // Fallback to the directory of the trigger file
            workingDir = triggerFile.getParent() != null ? triggerFile.getParent().getPath() : "";
        }

        executeJzeroGenCommandWithWorkingDir(project, triggerFile, command, workingDir);
    }

    private void executeJzeroGenCommandWithWorkingDir(@NotNull Project project,
                                                     @NotNull VirtualFile triggerFile,
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
        } catch (ExecutionException e) {
            // Handle execution errors
            throw new RuntimeException("Failed to execute jzero gen command", e);
        }
    }

    @Nullable
    private String findDescBasedWorkingDirectory(@NotNull String filePath) {
        // For files in desc/api, desc/proto, desc/sql, the working directory
        // should be the parent directory of "desc"
        // e.g., /path/to/desc/api/xx.api -> /path/to

        int descIndex = filePath.indexOf("/desc/");
        if (descIndex == -1) {
            // If no /desc/ found, fallback to finding .jzero.yaml
            return null;
        }

        // Return the path before /desc/
        return filePath.substring(0, descIndex);
    }

    @Nullable
    private String findJzeroConfigDirectory(@NotNull Project project, @NotNull VirtualFile startFile) {
        // Start from the current file directory and search upward for .jzero.yaml
        VirtualFile currentDir = startFile.getParent();

        while (currentDir != null) {
            VirtualFile configFile = currentDir.findChild(".jzero.yaml");
            if (configFile != null && configFile.exists()) {
                return currentDir.getPath();
            }

            // Move up to parent directory
            currentDir = currentDir.getParent();

            // Prevent infinite loop by limiting the search depth (e.g., up to 10 levels)
            // This is a safety measure to avoid going too far up the directory tree
            if (startFile.getPath().split("/").length - currentDir.getPath().split("/").length > 10) {
                break;
            }
        }

        return null;
    }

    @NotNull
    private String calculateRelativePath(@Nullable String basePath, @NotNull String fullPath) {
        if (basePath == null || basePath.isEmpty()) {
            return fullPath;
        }

        try {
            java.nio.file.Path base = java.nio.file.Paths.get(basePath).normalize();
            java.nio.file.Path absolute = java.nio.file.Paths.get(fullPath).normalize();
            java.nio.file.Path relative = base.relativize(absolute);
            return relative.toString();
        } catch (Exception e) {
            // If relative path calculation fails, fallback to absolute path
            return fullPath;
        }
    }

    @Override
    public void collectSlowLineMarkers(@NotNull List<? extends PsiElement> elements, @NotNull Collection<? super LineMarkerInfo<?>> result) {
        // Not needed for this implementation
    }
}