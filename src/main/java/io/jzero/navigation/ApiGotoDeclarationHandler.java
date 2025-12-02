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
 * GotoDeclarationHandler for @handler navigation to logic files
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

        // Calculate target path for logic files
        String targetPath;
        if (serviceInfo != null && serviceInfo.groupName != null) {
            // Get naming style from .jzero.yaml configuration
            String namingFormat = JzeroConfigReader.getNamingStyle(sourceElement.getProject(), sourceElement.getContainingFile());

            // Format the handler name according to jzero configuration
            String formattedHandlerName = JzeroConfigReader.formatFileName(namingFormat, handlerName);

            // Navigate to logic files instead of handler files
            targetPath = "internal/logic/" + serviceInfo.groupName + "/" + formattedHandlerName + ".go";
        } else {
            // Fallback to logic without group
            String namingFormat = JzeroConfigReader.getNamingStyle(sourceElement.getProject(), sourceElement.getContainingFile());
            String formattedHandlerName = JzeroConfigReader.formatFileName(namingFormat, handlerName);
            targetPath = "internal/logic/" + formattedHandlerName + ".go";
        }

        // Find and return the target PsiElement
        return findLogicPsiElements(sourceElement.getProject(), targetPath, handlerName, serviceInfo, sourceElement);
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

    private PsiElement[] findLogicPsiElements(@NotNull Project project, @NotNull String targetPath, @NotNull String handlerName, @Nullable ServiceInfo serviceInfo, @NotNull PsiElement sourceElement) {
        // Search for .go files in the project
        Collection<VirtualFile> goFiles = FilenameIndex.getAllFilesByExt(project, "go", GlobalSearchScope.projectScope(project));

        // Get naming format for consistent file naming
        String namingFormat = JzeroConfigReader.getNamingStyle(project, sourceElement.getContainingFile());
        String formattedHandlerName = JzeroConfigReader.formatFileName(namingFormat, handlerName);

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

        // If exact match fails, try broader search using formatted name
        for (VirtualFile file : goFiles) {
            String filePath = file.getPath();
            if (filePath.contains("logic") && filePath.contains(formattedHandlerName)) {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
                if (psiFile != null) {
                    return new PsiElement[]{psiFile};
                }
            }
        }

        return null;
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

    private static class ServiceInfo {
        String groupName;
    }
}