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
import io.jzero.util.JzeroConfigReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * LineMarker provider for proto rpc method navigation to logic files
 * Navigates from proto service rpc methods to internal/logic/$servicename/$rpcname.go
 * Based on ApiGotoDeclarationHandler pattern - shows icon in gutter for navigation
 */
public class ProtoGotoDeclarationHandler implements LineMarkerProvider {

    @Nullable
    @Override
    public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        // Check if this is a proto file
        PsiFile containingFile = element.getContainingFile();
        if (containingFile == null || !containingFile.getName().endsWith(".proto")) {
            return null;
        }

        // Try to find rpc method information
        RpcMethodInfo rpcInfo = findRpcMethodInfo(element);
        if (rpcInfo == null || rpcInfo.rpcName == null || rpcInfo.serviceName == null) {
            return null;
        }

        // Check if the logic file exists before showing the marker
        if (!logicFileExists(element, rpcInfo)) {
            return null;
        }

        return createNavigationMarkerForRpc(element, rpcInfo);
    }

    private LineMarkerInfo<?> createNavigationMarkerForRpc(@NotNull PsiElement element, @NotNull RpcMethodInfo rpcInfo) {
        return new LineMarkerInfo<>(
                element,
                element.getTextRange(),
                ApiIcon.FILE,
                e -> "Navigate to Logic: " + rpcInfo.rpcName,
                (e, elt) -> navigateToLogicFile(elt, rpcInfo),
                GutterIconRenderer.Alignment.LEFT,
                () -> "Go to " + rpcInfo.rpcName + " logic"
        );
    }

    private void navigateToLogicFile(@NotNull PsiElement sourceElement, @NotNull RpcMethodInfo rpcInfo) {
        // Get naming style from .jzero.yaml configuration
        PsiFile containingFile = sourceElement.getContainingFile();
        String namingFormat = JzeroConfigReader.getNamingStyle(sourceElement.getProject(), containingFile);

        // Service name always uses "gozero" style (lowercase without separators)
        String formattedServiceName = JzeroConfigReader.formatFileName("gozero", rpcInfo.serviceName);
        // Format the rpc name according to jzero configuration style
        String formattedRpcName = JzeroConfigReader.formatFileName(namingFormat, rpcInfo.rpcName);

        // Navigate to logic files: internal/logic/$servicename/$rpcname.go
        String targetPath = "internal/logic/" + formattedServiceName + "/" + formattedRpcName + ".go";

        // Find and navigate to the target logic file
        PsiFile targetFile = findLogicFile(sourceElement, targetPath, formattedServiceName, formattedRpcName);
        if (targetFile != null) {
            // Navigate to NewRpc function
            navigateToNewRpcFunction(sourceElement.getProject(), targetFile, rpcInfo.rpcName);
        }
    }

    @Nullable
    private PsiFile findLogicFile(@NotNull PsiElement sourceElement,
                                  @NotNull String targetPath,
                                  @NotNull String serviceName,
                                  @NotNull String rpcName) {
        Project project = sourceElement.getProject();

        // Get the base path from the proto file
        VirtualFile sourceFile = sourceElement.getContainingFile().getVirtualFile();
        if (sourceFile == null) {
            return null;
        }

        // Calculate the base path by replacing "desc/proto" or "proto" with "internal/logic"
        String filePath = sourceFile.getPath();
        String basePath = filePath;

        // Replace "desc/proto" or "proto" with "internal/logic"
        if (filePath.contains("/desc/proto/")) {
            basePath = filePath.substring(0, filePath.indexOf("/desc/proto/")) + "/internal/logic";
        } else if (filePath.contains("/proto/")) {
            basePath = filePath.substring(0, filePath.indexOf("/proto/")) + "/internal/logic";
        }

        // Combine base path with target path (which already contains the relative part)
        String fullPath = basePath + "/" + targetPath.substring("internal/logic".length());

        // Try to find the file at the calculated path
        VirtualFile targetVirtualFile = sourceFile.getFileSystem().findFileByPath(fullPath);
        if (targetVirtualFile != null) {
            return PsiManager.getInstance(project).findFile(targetVirtualFile);
        }

        return null;
    }

    private void navigateToNewRpcFunction(@NotNull Project project, @NotNull PsiFile goFile, @NotNull String rpcName) {
        String content = goFile.getText();
        // Capitalize first letter for function name (e.g., "getuser" -> "Getuser")
        String functionName = "New" + rpcName.substring(0, 1).toUpperCase() + rpcName.substring(1);
        String functionSearch = "func " + functionName + "(";

        int functionIndex = content.indexOf(functionSearch);
        if (functionIndex != -1) {
            openFileAndNavigate(project, goFile.getVirtualFile(), functionIndex);
        } else {
            // Fallback to file beginning if function not found
            openFileAndNavigate(project, goFile.getVirtualFile(), 0);
        }
    }

    private void openFileAndNavigate(@NotNull Project project, @NotNull VirtualFile file, int targetOffset) {
        com.intellij.openapi.fileEditor.OpenFileDescriptor descriptor =
            new com.intellij.openapi.fileEditor.OpenFileDescriptor(
                project,
                file,
                targetOffset
            );
        descriptor.navigate(true);
    }

    @Nullable
    private RpcMethodInfo findRpcMethodInfo(@NotNull PsiElement element) {
        // Navigate through the PSI tree to find rpc method and service declarations
        // Proto files structure: service ServiceName { rpc RpcName (...) returns (...); }

        String rpcName = null;
        String serviceName = null;

        // Check if current element is the rpc method name identifier
        // The element should be an identifier that comes right after "rpc" keyword
        String elementText = element.getText();
        if (elementText != null && elementText.matches("[a-zA-Z][a-zA-Z0-9_]*")) {
            // Check if the previous sibling is "rpc" keyword
            PsiElement prev = element.getPrevSibling();
            while (prev != null && (prev.getText() == null || prev.getText().trim().isEmpty())) {
                prev = prev.getPrevSibling();
            }

            if (prev != null && "rpc".equals(prev.getText().trim())) {
                // This is the rpc method name
                rpcName = elementText;
            }
        }

        // If we found an rpc name, look for the service name
        if (rpcName != null) {
            PsiElement current = element;
            while (current != null) {
                String text = current.getText();
                if (text != null && (text.trim().startsWith("service ") || text.contains("service "))) {
                    serviceName = extractServiceName(current);
                    if (serviceName != null) {
                        break;
                    }
                }
                current = current.getParent();
            }
        }

        if (rpcName == null || serviceName == null) {
            return null;
        }

        RpcMethodInfo info = new RpcMethodInfo();
        info.rpcName = rpcName;
        info.serviceName = serviceName;
        return info;
    }

    @Nullable
    private String extractServiceName(@NotNull PsiElement element) {
        // Extract service name from element text
        // Expected format: "service ServiceName {"
        String text = element.getText();
        if (text == null) {
            return null;
        }

        // Simple extraction: look for "service " followed by identifier
        String[] parts = text.trim().split("\\s+");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("service".equals(parts[i])) {
                String name = parts[i + 1];
                // Remove any trailing characters like '{' or '}'
                name = name.replaceAll("[^a-zA-Z0-9_].*", "");
                if (!name.isEmpty()) {
                    return name;
                }
            }
        }

        // Alternative: check if element itself is the identifier after "service"
        PsiElement prev = element.getPrevSibling();
        while (prev != null) {
            if (prev.getText() != null && prev.getText().trim().equals("service")) {
                String name = element.getText();
                if (name != null && name.matches("[a-zA-Z][a-zA-Z0-9_]*")) {
                    return name;
                }
            }
            prev = prev.getPrevSibling();
        }

        return null;
    }

    @Override
    public void collectSlowLineMarkers(@NotNull java.util.List<? extends PsiElement> elements, @NotNull java.util.Collection<? super LineMarkerInfo<?>> result) {
        // Not needed for this implementation
    }

    private boolean logicFileExists(@NotNull PsiElement element, @NotNull RpcMethodInfo rpcInfo) {
        // Get naming style from .jzero.yaml configuration
        PsiFile containingFile = element.getContainingFile();
        String namingFormat = JzeroConfigReader.getNamingStyle(element.getProject(), containingFile);

        // Service name always uses "gozero" style (lowercase without separators)
        String formattedServiceName = JzeroConfigReader.formatFileName("gozero", rpcInfo.serviceName);
        // Format the rpc name according to jzero configuration style
        String formattedRpcName = JzeroConfigReader.formatFileName(namingFormat, rpcInfo.rpcName);

        // Navigate to logic files: internal/logic/$servicename/$rpcname.go
        String targetPath = "internal/logic/" + formattedServiceName + "/" + formattedRpcName + ".go";

        return findLogicFile(element, targetPath, formattedServiceName, formattedRpcName) != null;
    }

    private static class RpcMethodInfo {
        String rpcName;
        String serviceName;
    }
}
