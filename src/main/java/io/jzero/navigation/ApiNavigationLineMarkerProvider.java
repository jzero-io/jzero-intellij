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
import com.intellij.icons.AllIcons;
import io.jzero.antlr4.ApiParser;
import io.jzero.psi.nodes.ServiceNode;
import io.jzero.psi.nodes.ServiceRouteNode;
import io.jzero.psi.nodes.HandlerValueNode;
import io.jzero.parser.ApiParserDefinition;
import io.jzero.util.JzeroConfigReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * LineMarker provider for API navigation to logic files
 */
public class ApiNavigationLineMarkerProvider implements LineMarkerProvider {

    @Nullable
    @Override
    public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        // Note: Handler navigation is now handled by ApiGotoDeclarationHandler
        // No longer show LineMarker for HandlerValueNode
        if (element instanceof HandlerValueNode) {
            return null;
        }

        // Look for http route nodes - navigate to logic files
        if (element instanceof ServiceRouteNode) {
            return createLogicNavigationMarkerForRoute(element, (ServiceRouteNode) element);
        }

        return null;
    }

    private LineMarkerInfo<?> createLogicNavigationMarkerForRoute(@NotNull PsiElement element, @NotNull ServiceRouteNode routeNode) {
        // For route nodes, try to find the associated handler
        String handlerName = findHandlerForRoute(routeNode);
        if (handlerName == null) {
            return null;
        }

        // Get naming style from .jzero.yaml configuration
        String namingFormat = JzeroConfigReader.getNamingStyle(element.getProject(), element.getContainingFile());
        String formattedHandlerName = JzeroConfigReader.formatFileName(namingFormat, handlerName);

        ServiceInfo serviceInfo = findServiceInfo(routeNode);
        String targetPath;
        if (serviceInfo != null && serviceInfo.groupName != null) {
            // Format the file name according to jzero configuration
            targetPath = "internal/logic/" + serviceInfo.groupName + "/" + formattedHandlerName + ".go";
        } else {
            // Format the file name according to jzero configuration
            targetPath = "internal/logic/" + formattedHandlerName + ".go";
        }

        return new LineMarkerInfo<>(
                element,
                element.getTextRange(),
                AllIcons.Nodes.Function,
                e -> "Navigate to Logic: " + handlerName + " → " + targetPath,
                (e, elt) -> navigateToLogicFile(elt.getProject(), targetPath, handlerName),
                GutterIconRenderer.Alignment.LEFT,
                () -> "Go to " + handlerName + " logic implementation"
        );
    }

    private ServiceInfo findServiceInfo(@NotNull PsiElement element) {
        // Navigate up to find the service node
        PsiElement current = element.getParent();
        while (current != null) {
            if (current instanceof ServiceNode) {
                return extractServiceInfo((ServiceNode) current);
            }
            current = current.getParent();
        }
        return null;
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
            if (line.contains("prefix:")) {
                String prefixValue = line.substring(line.indexOf("prefix:") + 7).trim();
                if (prefixValue.startsWith("\"") && prefixValue.endsWith("\"")) {
                    prefixValue = prefixValue.substring(1, prefixValue.length() - 1);
                }
                info.prefix = prefixValue;
            }
            if (line.contains("compact_handler:")) {
                String compactValue = line.substring(line.indexOf("compact_handler:") + 16).trim();
                // Remove quotes if present
                if (compactValue.startsWith("\"") && compactValue.endsWith("\"")) {
                    compactValue = compactValue.substring(1, compactValue.length() - 1);
                }
                // Support various boolean values: true, false, yes, no
                info.compactHandler = "true".equalsIgnoreCase(compactValue) ||
                                   "yes".equalsIgnoreCase(compactValue) ||
                                   "1".equals(compactValue);
            }
        }

        // Return info if we have either group or compact_handler configuration
        return (info.groupName != null) ? info : null;
    }

    private String findHandlerForRoute(@NotNull ServiceRouteNode routeNode) {
        // Look for @handler annotation before this route
        PsiElement current = routeNode.getPrevSibling();
        while (current != null) {
            String text = current.getText();
            if (text != null && text.contains("@handler")) {
                // Extract handler name
                String[] lines = text.split("\n");
                for (String line : lines) {
                    if (line.contains("@handler")) {
                        String handlerLine = line.trim();
                        if (handlerLine.contains("@handler")) {
                            return handlerLine.substring(handlerLine.indexOf("@handler") + 8).trim();
                        }
                    }
                }
            }
            current = current.getPrevSibling();
        }
        return null;
    }

    private void navigateToLogicFile(@NotNull Project project, @NotNull String targetPath, @NotNull String handlerName) {
        // Search for .go files in the project
        Collection<VirtualFile> goFiles = FilenameIndex.getAllFilesByExt(project, "go", GlobalSearchScope.projectScope(project));

        // Get naming format for consistent file naming
        String namingFormat = JzeroConfigReader.getNamingStyle(project, null);
        String formattedHandlerName = JzeroConfigReader.formatFileName(namingFormat, handlerName);

        // First try exact path match
        for (VirtualFile file : goFiles) {
            String filePath = file.getPath();
            if (filePath.contains(targetPath.replace("*", ""))) {
                openFileAndNavigateToFunction(project, file, handlerName);
                return;
            }
        }

        // If exact match fails, try broader search using formatted name
        for (VirtualFile file : goFiles) {
            String filePath = file.getPath();
            if (filePath.contains("logic") && filePath.contains(formattedHandlerName)) {
                openFileAndNavigateToFunction(project, file, handlerName);
                return;
            }
        }

        // Final fallback: just open first logic file found
        for (VirtualFile file : goFiles) {
            if (file.getPath().contains("logic")) {
                openFile(project, file);
                return;
            }
        }
    }

    private void openFileAndNavigateToFunction(@NotNull Project project, @NotNull VirtualFile file, @NotNull String functionName) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (psiFile != null) {
            // Search for the function in the file
            String content = psiFile.getText();
            String[] lines = content.split("\n");

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.startsWith("func ") && line.contains(functionName)) {
                    // Navigate to this line
                    int offset = 0;
                    for (int j = 0; j < i; j++) {
                        offset += lines[j].length() + 1;
                    }

                    com.intellij.openapi.fileEditor.OpenFileDescriptor descriptor =
                        new com.intellij.openapi.fileEditor.OpenFileDescriptor(
                            project,
                            file,
                            offset
                        );
                    descriptor.navigate(false);
                    return;
                }
            }

            // If function not found, just open the file
            openFile(project, file);
        }
    }

    private void openFile(@NotNull Project project, @NotNull VirtualFile file) {
        com.intellij.openapi.fileEditor.OpenFileDescriptor descriptor =
            new com.intellij.openapi.fileEditor.OpenFileDescriptor(
                project,
                file,
                0
            );
        descriptor.navigate(false);
    }

    private static class ServiceInfo {
        String groupName;
        String prefix;
        boolean compactHandler;
    }
}