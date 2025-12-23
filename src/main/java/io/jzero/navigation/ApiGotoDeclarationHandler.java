package io.jzero.navigation;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import io.jzero.icon.ApiIcon;
import io.jzero.psi.nodes.HandlerValueNode;
import io.jzero.psi.nodes.ServiceNode;
import io.jzero.util.JzeroConfigReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * LineMarker provider for handler navigation to logic files
 * Based on TypeGotoDeclarationHandler pattern - shows icon in gutter for navigation
 */
public class ApiGotoDeclarationHandler implements LineMarkerProvider {

    @Nullable
    @Override
    public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        // Only look for handler value nodes for navigation to logic files
        if (element instanceof HandlerValueNode) {
            return createNavigationMarkerForHandler(element, (HandlerValueNode) element);
        }

        return null;
    }

    private LineMarkerInfo<?> createNavigationMarkerForHandler(@NotNull PsiElement element, @NotNull HandlerValueNode handlerNode) {
        String handlerName = element.getText();
        if (handlerName == null || handlerName.trim().isEmpty()) {
            return null;
        }

        // Remove "Handler" suffix if present for display
        String displayName = handlerName;
        if (handlerName.endsWith("Handler")) {
            displayName = handlerName.substring(0, handlerName.length() - "Handler".length());
        }
        final String finalDisplayName = displayName;

        return new LineMarkerInfo<>(
                element,
                element.getTextRange(),
                ApiIcon.FILE,
                e -> "Navigate to Logic: " + finalDisplayName,
                (e, elt) -> navigateToLogicFile(elt, handlerName),
                GutterIconRenderer.Alignment.LEFT,
                () -> "Go to " + finalDisplayName + " logic"
        );
    }

    private void navigateToLogicFile(@NotNull PsiElement sourceElement, @NotNull String handlerName) {
        // Remove "Handler" suffix if present
        if (handlerName.endsWith("Handler")) {
            handlerName = handlerName.substring(0, handlerName.length() - "Handler".length());
        }

        // Find service information
        ServiceInfo serviceInfo = findServiceInfo(sourceElement);

        // Calculate target path for logic files
        String targetPath;
        if (serviceInfo != null && serviceInfo.groupName != null) {
            // Get naming style from .jzero.yaml configuration
            String namingFormat = JzeroConfigReader.getNamingStyle(sourceElement.getProject(), sourceElement.getContainingFile());

            // Format the handler name according to jzero configuration
            String formattedHandlerName = JzeroConfigReader.formatFileName(namingFormat, handlerName);

            // Navigate to logic files
            targetPath = "internal/logic/" + serviceInfo.groupName + "/" + formattedHandlerName + ".go";
        } else {
            // Fallback to logic without group
            String namingFormat = JzeroConfigReader.getNamingStyle(sourceElement.getProject(), sourceElement.getContainingFile());
            String formattedHandlerName = JzeroConfigReader.formatFileName(namingFormat, handlerName);
            targetPath = "internal/logic/" + formattedHandlerName + ".go";
        }

        // Find and navigate to the target logic file
        PsiFile targetFile = findLogicFile(sourceElement.getProject(), targetPath, handlerName, sourceElement);
        if (targetFile != null) {
            navigateToFile(sourceElement.getProject(), targetFile.getVirtualFile());
        }
    }

    @Nullable
    private PsiFile findLogicFile(@NotNull Project project, @NotNull String targetPath, @NotNull String handlerName, @NotNull PsiElement sourceElement) {
        // Search for .go files in the project
        Collection<VirtualFile> goFiles = FilenameIndex.getAllFilesByExt(project, "go", GlobalSearchScope.projectScope(project));

        // Get naming format for consistent file naming
        String namingFormat = JzeroConfigReader.getNamingStyle(project, sourceElement.getContainingFile());
        String formattedHandlerName = JzeroConfigReader.formatFileName(namingFormat, handlerName);

        // First try exact path match
        for (VirtualFile file : goFiles) {
            String filePath = file.getPath();
            if (filePath.contains(targetPath)) {
                return PsiManager.getInstance(project).findFile(file);
            }
        }

        // If exact match fails, try broader search using formatted name
        for (VirtualFile file : goFiles) {
            String filePath = file.getPath();
            if (filePath.contains("logic") && filePath.contains(formattedHandlerName)) {
                return PsiManager.getInstance(project).findFile(file);
            }
        }

        return null;
    }

    private void navigateToFile(@NotNull Project project, @NotNull VirtualFile file) {
        com.intellij.openapi.fileEditor.OpenFileDescriptor descriptor =
            new com.intellij.openapi.fileEditor.OpenFileDescriptor(
                project,
                file,
                0
            );
        descriptor.navigate(true);
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
        }

        return info.groupName != null ? info : null;
    }

    @Override
    public void collectSlowLineMarkers(@NotNull java.util.List<? extends PsiElement> elements, @NotNull java.util.Collection<? super LineMarkerInfo<?>> result) {
        // Not needed for this implementation
    }

    private static class ServiceInfo {
        String groupName;
    }
}
