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
import com.intellij.psi.util.PsiTreeUtil;
import io.jzero.util.JzeroConfigReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * GotoDeclarationHandler for proto rpc method navigation to logic files
 * Navigates from proto service rpc methods to internal/logic/$servicename/$rpcname.go
 */
public class ProtoGotoDeclarationHandler implements GotoDeclarationHandler {

    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(@NotNull PsiElement sourceElement, int offset, @NotNull Editor editor) {
        // Check if this is a proto file
        PsiFile containingFile = sourceElement.getContainingFile();
        if (containingFile == null || !containingFile.getName().endsWith(".proto")) {
            return null;
        }

        // Try to find rpc method information
        RpcMethodInfo rpcInfo = findRpcMethodInfo(sourceElement);
        if (rpcInfo == null || rpcInfo.rpcName == null || rpcInfo.serviceName == null) {
            return null;
        }

        // Get naming style from .jzero.yaml configuration
        String namingFormat = JzeroConfigReader.getNamingStyle(sourceElement.getProject(), containingFile);

        // Service name always uses "gozero" style (lowercase without separators)
        String formattedServiceName = JzeroConfigReader.formatFileName("gozero", rpcInfo.serviceName);
        // Format the rpc name according to jzero configuration style
        String formattedRpcName = JzeroConfigReader.formatFileName(namingFormat, rpcInfo.rpcName);

        // Navigate to logic files: internal/logic/$servicename/$rpcname.go
        String targetPath = "internal/logic/" + formattedServiceName + "/" + formattedRpcName + ".go";

        // Find and return the target PsiElement
        return findLogicPsiElements(sourceElement.getProject(), targetPath, formattedServiceName, formattedRpcName);
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
    private String extractRpcName(@NotNull PsiElement element) {
        // Extract rpc method name from element text
        // Expected format: "rpc MethodName (RequestType) returns (ResponseType);"
        String text = element.getText();
        if (text == null) {
            return null;
        }

        // Simple extraction: look for "rpc " followed by identifier
        String[] parts = text.trim().split("\\s+");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("rpc".equals(parts[i])) {
                String name = parts[i + 1];
                // Remove any trailing characters like '(' or '{'
                name = name.replaceAll("[^a-zA-Z0-9_].*", "");
                if (!name.isEmpty()) {
                    return name;
                }
            }
        }

        // Alternative: check if element itself is the identifier after "rpc"
        PsiElement prev = element.getPrevSibling();
        while (prev != null) {
            if (prev.getText() != null && prev.getText().trim().equals("rpc")) {
                String name = element.getText();
                if (name != null && name.matches("[a-zA-Z][a-zA-Z0-9_]*")) {
                    return name;
                }
            }
            prev = prev.getPrevSibling();
        }

        return null;
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

    private PsiElement[] findLogicPsiElements(@NotNull Project project,
                                              @NotNull String targetPath,
                                              @NotNull String serviceName,
                                              @NotNull String rpcName) {
        // Search for .go files in the project
        Collection<VirtualFile> goFiles = FilenameIndex.getAllFilesByExt(project, "go", GlobalSearchScope.projectScope(project));

        // First try exact path match
        for (VirtualFile file : goFiles) {
            String filePath = file.getPath();
            if (filePath.contains(targetPath)) {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
                if (psiFile != null) {
                    return new PsiElement[]{psiFile};
                }
            }
        }

        // If exact match fails, try broader search using formatted names
        for (VirtualFile file : goFiles) {
            String filePath = file.getPath();
            // Check if path contains both logic directory and the rpc name
            if (filePath.contains("logic") &&
                filePath.contains("/" + serviceName + "/") &&
                filePath.contains(rpcName)) {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
                if (psiFile != null) {
                    return new PsiElement[]{psiFile};
                }
            }
        }

        // Final fallback: just look for the rpc name in logic directories
        for (VirtualFile file : goFiles) {
            String filePath = file.getPath();
            if (filePath.contains("logic") && filePath.contains(rpcName)) {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
                if (psiFile != null) {
                    return new PsiElement[]{psiFile};
                }
            }
        }

        return null;
    }

    private static class RpcMethodInfo {
        String rpcName;
        String serviceName;
    }
}