package io.jzero.navigation;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import io.jzero.language.ApiFileType;
import io.jzero.psi.nodes.HandlerValueNode;
import io.jzero.psi.nodes.ServiceNode;
import io.jzero.util.JzeroConfigReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * GotoDeclarationHandler for @handler navigation
 */
public class ApiGotoDeclarationHandler implements GotoDeclarationHandler {

    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(@NotNull PsiElement sourceElement, int offset, @NotNull Editor editor) {
        // Check if this element or its parent is within a handler context
        HandlerValueNode handlerNode = findHandlerValueNode(sourceElement);
        if (handlerNode == null) {
            return null;
        }

        String handlerName = handlerNode.getText();

        if (handlerName == null || handlerName.trim().isEmpty()) {
            return null;
        }

        // Find service information
        ServiceInfo serviceInfo = findServiceInfo(handlerNode);

        // Calculate target path
        String targetPath;
        if (serviceInfo != null && serviceInfo.groupName != null) {
            // Get naming style from .jzero.yaml configuration
            String namingFormat = JzeroConfigReader.getNamingStyle(sourceElement.getProject(), sourceElement.getContainingFile());

            // Format the handler name according to jzero configuration
            String formattedHandlerName = JzeroConfigReader.formatFileName(namingFormat, handlerName);

            // For nested groups like "manage/menu", we need to handle directory structure properly
            String groupPath = serviceInfo.groupName;
            String lastSegment = groupPath;

            // Extract the last segment from the group path for compact file naming
            if (groupPath.contains("/")) {
                String[] segments = groupPath.split("/");
                lastSegment = segments[segments.length - 1];
            }

            // Format the last segment for the compact file name
            String formattedLastSegment = JzeroConfigReader.formatFileName(namingFormat, lastSegment);

            if (serviceInfo.compactHandler) {
                // Compact handler: internal/handler/manage/menu/menu_compact.go
                targetPath = "internal/handler/" + groupPath + "/" + formattedLastSegment + "_compact.go";
            } else {
                // Normal handler: internal/handler/manage/menu/{handler}.go
                targetPath = "internal/handler/" + groupPath + "/" + formattedHandlerName + ".go";
            }
        } else {
            return null;
        }

        // Find and return the target PsiElement
        return findHandlerPsiElements(sourceElement.getProject(), targetPath, handlerName, serviceInfo, sourceElement);
    }

    @Nullable
    private HandlerValueNode findHandlerValueNode(@NotNull PsiElement element) {
        // First check if the element itself is a HandlerValueNode
        if (element instanceof HandlerValueNode) {
            return (HandlerValueNode) element;
        }

        // Then check its parent and ancestors
        PsiElement current = element.getParent();
        while (current != null) {
            if (current instanceof HandlerValueNode) {
                return (HandlerValueNode) current;
            }
            current = current.getParent();
        }

        // Also check children in case we're on a parent element
        return findChildHandlerValueNode(element);
    }

    @Nullable
    private HandlerValueNode findChildHandlerValueNode(@NotNull PsiElement element) {
        // Search in the element's children for HandlerValueNode
        for (PsiElement child : element.getChildren()) {
            if (child instanceof HandlerValueNode) {
                return (HandlerValueNode) child;
            }
            // Recursively search
            HandlerValueNode found = findChildHandlerValueNode(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private PsiElement[] findHandlerPsiElements(@NotNull Project project, @NotNull String targetPath, @NotNull String handlerName, @Nullable ServiceInfo serviceInfo, @NotNull PsiElement sourceElement) {
        // Get the API file path to determine the project root
        PsiFile apiFile = sourceElement.getContainingFile();
        if (apiFile == null) {
            return null;
        }

        String apiFilePath = apiFile.getVirtualFile().getPath();
        // Extract project root path from /path/to/desc/api/manage/menu.api -> /path/to
        int descIndex = apiFilePath.indexOf("/desc/");
        if (descIndex == -1) {
            return null;
        }
        String projectRoot = apiFilePath.substring(0, descIndex);

        // Construct the full target file path
        String fullTargetPath = projectRoot + "/" + targetPath;

        // Try to find the file directly using LocalFileSystem
        com.intellij.openapi.vfs.LocalFileSystem fs = com.intellij.openapi.vfs.LocalFileSystem.getInstance();
        VirtualFile targetFile = fs.findFileByPath(fullTargetPath);

        if (targetFile != null && targetFile.exists()) {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(targetFile);
            if (psiFile != null) {
                // For compact handlers, try to find the specific function
                if (serviceInfo != null && serviceInfo.compactHandler) {
                    int lineNumber = findFunctionLineNumber(psiFile, handlerName);
                    if (lineNumber >= 0) {
                        // For compact handlers, we need to navigate to specific functions
                        // Create a synthetic element with position information
                        return createNavigationTarget(psiFile, targetFile, lineNumber, handlerName);
                    }
                }
                // Fallback to file level navigation
                return new PsiElement[]{psiFile};
            }
        }

        return null;
    }

    private int findFunctionLineNumber(@NotNull PsiFile psiFile, @NotNull String functionName) {
        try {
            String content = psiFile.getText();
            String[] lines = content.split("\\n");

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                // Look for "func functionName(" pattern using regex
                if (line.startsWith("func " + functionName + "(")) {
                    return i; // Return the line number (0-based)
                }
            }
        } catch (Exception e) {
            // If parsing fails, return -1
        }

        return -1; // Not found
    }

    private PsiElement[] createNavigationTarget(@NotNull PsiFile psiFile, @NotNull VirtualFile file, int lineNumber, @NotNull String handlerName) {
        // Create a custom navigation target that includes position information
        // For now, let's try using the file itself with a custom navigation approach
        try {
            // Use OpenFileDescriptor to navigate to the specific line
            int offset = calculateLineOffset(psiFile, lineNumber);
            com.intellij.openapi.fileEditor.OpenFileDescriptor descriptor =
                new com.intellij.openapi.fileEditor.OpenFileDescriptor(
                    psiFile.getProject(), file, offset);

            // Schedule the navigation to happen after the current action
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                descriptor.navigate(true);
            });

        } catch (Exception e) {
            // If navigation fails, fall back to file level
        }

        // Return the file as the navigation target
        return new PsiElement[]{psiFile};
    }

    @Nullable
    private PsiElement findFunctionElement(@NotNull PsiFile psiFile, int lineNumber) {
        try {
            // For now, just return null to force file-level navigation
            // The complex PSI element navigation seems to cause issues
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private int calculateLineOffset(@NotNull PsiFile psiFile, int lineNumber) {
        try {
            String content = psiFile.getText();
            String[] lines = content.split("\\n");

            // Calculate the offset for the target line
            int offset = 0;
            for (int i = 0; i < lineNumber; i++) {
                offset += lines[i].length() + 1; // +1 for newline
            }
            return offset;
        } catch (Exception e) {
            return 0; // Fallback to beginning of file
        }
    }

    @Nullable
    private PsiElement findElementAtLine(@NotNull PsiFile psiFile, int lineNumber) {
        try {
            String content = psiFile.getText();
            String[] lines = content.split("\\n");

            // Calculate the offset for the target line
            int offset = 0;
            for (int i = 0; i < lineNumber; i++) {
                offset += lines[i].length() + 1; // +1 for newline
            }

            String targetLine = lines[lineNumber];
            int funcKeywordPos = targetLine.indexOf("func ");
            if (funcKeywordPos >= 0) {
                // Find element at the "func" keyword position
                return psiFile.findElementAt(offset + funcKeywordPos);
            } else {
                // Fallback to line start
                return psiFile.findElementAt(offset);
            }
        } catch (Exception e) {
            // If calculation fails, return null
            return null;
        }
    }

    private ServiceInfo findServiceInfo(@NotNull PsiElement element) {
        // Navigate up to find the service node
        PsiElement current = element.getParent();
        while (current != null && !(current instanceof ServiceNode)) {
            current = current.getParent();
        }

        if (current == null) {
            return null;
        }

        ServiceNode serviceNode = (ServiceNode) current;
        return extractServiceInfo(serviceNode);
    }

    private ServiceInfo extractServiceInfo(@NotNull ServiceNode serviceNode) {
        // Look for @server annotation before the service
        PsiElement current = serviceNode.getPrevSibling();
        while (current != null) {
            String text = current.getText();
            if (text != null && text.contains("@server")) {
                return parseServerAnnotation(text);
            }
            current = current.getPrevSibling();
        }
        return null;
    }

    private ServiceInfo parseServerAnnotation(@NotNull String annotationText) {
        ServiceInfo info = new ServiceInfo();

        // Parse @server configuration values
        String[] lines = annotationText.split("\n");
        for (String line : lines) {
            line = line.trim();

            if (line.contains("group:")) {
                String groupValue = line.substring(line.indexOf("group:") + 6).trim();
                // Remove quotes if present
                if (groupValue.startsWith("\"") && groupValue.endsWith("\"")) {
                    groupValue = groupValue.substring(1, groupValue.length() - 1);
                }
                info.groupName = groupValue;
            }
            if (line.contains("compact_handler:")) {
                String compactValue = line.substring(line.indexOf("compact_handler:") + 16).trim();
                // Remove quotes if present
                if (compactValue.startsWith("\"") && compactValue.endsWith("\"")) {
                    compactValue = compactValue.substring(1, compactValue.length() - 1);
                }
                info.compactHandler = "true".equalsIgnoreCase(compactValue) ||
                                   "yes".equalsIgnoreCase(compactValue) ||
                                   "1".equals(compactValue);
            }
        }

        return info.groupName != null ? info : null;
    }

    private static class ServiceInfo {
        String groupName;
        boolean compactHandler;
    }
}