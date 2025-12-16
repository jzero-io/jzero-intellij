package io.jzero.action;

import io.jzero.util.Exec;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.CreateFileFromTemplateAction;
import com.intellij.ide.actions.CreateFileFromTemplateDialog;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileTypes.FileTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ProtoCreateFileAction extends CreateFileFromTemplateAction implements DumbAware {
    public static final String FILE_TEMPLATE = "Proto File";
    private static final String NEW_PROTO_FILE = "New Proto File";

    private static final String DEFAULT_PROTO_TEMPLATE_PROPERTY = "DefaultProtoTemplateProperty";

    public ProtoCreateFileAction() {
        super(NEW_PROTO_FILE, "", AllIcons.FileTypes.Text);
    }

    @Override
    protected void buildDialog(@NotNull Project project, @NotNull PsiDirectory directory, CreateFileFromTemplateDialog.@NotNull Builder builder) {
        builder.setTitle(FILE_TEMPLATE)
                .addKind(FILE_TEMPLATE, AllIcons.FileTypes.Text, FILE_TEMPLATE);
    }

    @Override
    protected @NlsContexts.Command String getActionName(PsiDirectory directory, @NotNull String newName, String templateName) {
        return NEW_PROTO_FILE;
    }

    @Override
    protected @Nullable String getDefaultTemplateProperty() {
        return DEFAULT_PROTO_TEMPLATE_PROPERTY;
    }

    @Override
    protected PsiFile createFileFromTemplate(@NotNull String name, @NotNull com.intellij.ide.fileTemplates.FileTemplate template, @NotNull PsiDirectory directory) {
        Project project = directory.getProject();

        String workingDir = findWorkingDirectory(project, directory.getVirtualFile());
        if (workingDir == null) {
            // Fallback to the directory where we're creating the file
            workingDir = directory.getVirtualFile().getPath();
        }

        // Extract relative path from working directory for the proto name
        String protoName = extractRelativeProtoName(directory.getVirtualFile(), workingDir, name);

        String jzeroCommand = "jzero add proto " + protoName + " -o std --quiet";

        Exec.ExecResult result = Exec.run(project, jzeroCommand, workingDir);

        if (result == null) {
            Notifications.Bus.notify(new com.intellij.notification.Notification(
                "Jzero Plugin", "Error", "Exec.run returned null",
                NotificationType.ERROR), project);
            return null;
        }

        if (result.getExitCode() != 0) {
            String errorMsg = result.getStderr() != null ? result.getStderr() : "Unknown error";
            String stdoutMsg = result.getStdout() != null ? result.getStdout() : "No stdout";
            Notifications.Bus.notify(new com.intellij.notification.Notification(
                "Jzero Plugin", "Error",
                "Failed to execute jzero add proto command\n" +
                "Command: " + jzeroCommand + "\n" +
                "Working directory: " + workingDir + "\n" +
                "Exit code: " + result.getExitCode() + "\n" +
                "Stderr: " + errorMsg + "\n" +
                "Stdout: " + stdoutMsg,
                NotificationType.ERROR), project);
            return null;
        }

        String jzeroContent = result.getStdout();
        if (jzeroContent == null || jzeroContent.trim().isEmpty()) {
            String errorMsg = result.getStderr() != null ? result.getStderr() : "No stderr";
            Notifications.Bus.notify(new com.intellij.notification.Notification(
                "Jzero Plugin", "Warning",
                "jzero add proto returned empty content\n" +
                "Command: " + jzeroCommand + "\n" +
                "Working directory: " + workingDir + "\n" +
                "Stdout: " + (jzeroContent != null ? jzeroContent : "null") + "\n" +
                "Stderr: " + errorMsg,
                NotificationType.WARNING), project);
            return null;
        }

        try {
            // Create the file with the rendered content
            PsiFileFactory factory = PsiFileFactory.getInstance(project);
            PsiFile file = factory.createFileFromText(name + ".proto", FileTypes.PLAIN_TEXT, jzeroContent);

            PsiFile createdFile = (PsiFile) directory.add(file);
            return createdFile;
        } catch (Exception e) {
            Notifications.Bus.notify(new com.intellij.notification.Notification(
                "Jzero Plugin", "Error", "Failed to create proto file: " + e.getMessage(),
                NotificationType.ERROR), project);
            return null;
        }
    }

    @Nullable
    private String extractRelativeProtoName(@NotNull VirtualFile targetFile, @NotNull String workingDir, @NotNull String fileName) {
        // Get the full path of the target directory
        String dirPath = targetFile.getPath();

        // Remove the working directory prefix to get relative path
        if (dirPath.startsWith(workingDir)) {
            String relativePath = dirPath.substring(workingDir.length());

            // Remove leading slash if present
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }

            // Check if the file is in desc/proto folder
            if (relativePath.startsWith("desc/proto/")) {
                // Remove "desc/proto" prefix
                relativePath = relativePath.substring("desc/proto/".length());

                // If relative path is empty, just return the file name
                if (relativePath.isEmpty()) {
                    return fileName;
                }

                // Combine relative path with file name
                return relativePath + "/" + fileName;
            } else if (relativePath.equals("desc/proto")) {
                // Directly in desc/proto folder
                return fileName;
            } else {
                // Not in desc/proto folder, use file name directly
                return fileName;
            }
        }

        // Fallback to file name if working directory is not a prefix
        return fileName;
    }

    @Nullable
    private String findWorkingDirectory(@NotNull Project project, @NotNull VirtualFile startDir) {
        // Get the base directory path (where we're creating the file)
        String path = startDir.getPath();

        // If this is a directory where we're creating the proto file,
        // we need to find the project root by going up levels until we find a level before "desc"
        // Example: /path/to/desc/proto/v1 -> /path/to

        String[] pathSegments = path.split("/");

        // Find "desc" in the path and return the directory before it
        for (int i = 0; i < pathSegments.length - 1; i++) {
            if (pathSegments[i + 1].equals("desc")) {
                // Reconstruct path up to the directory before "desc"
                StringBuilder result = new StringBuilder();
                for (int j = 0; j <= i; j++) {
                    if (!pathSegments[j].isEmpty()) {
                        result.append("/").append(pathSegments[j]);
                    }
                }
                return result.length() > 0 ? result.toString() : "/";
            }
        }

        // If "desc" not found, fallback to removing the last segment
        int lastSeparatorIndex = path.lastIndexOf('/');
        if (lastSeparatorIndex > 0) {
            return path.substring(0, lastSeparatorIndex);
        }

        // Fallback to current directory if no separator found
        return startDir.getPath();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ProtoCreateFileAction;
    }
}
