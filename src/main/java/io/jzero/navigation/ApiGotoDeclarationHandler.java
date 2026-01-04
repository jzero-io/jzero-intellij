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
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.ui.awt.RelativePoint;
import io.jzero.icon.ApiIcon;
import io.jzero.psi.nodes.HandlerValueNode;
import io.jzero.psi.nodes.ServiceNode;
import io.jzero.util.JzeroConfigReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * LineMarker provider for handler and middleware navigation
 * Based on TypeGotoDeclarationHandler pattern - shows icon in gutter for navigation
 */
public class ApiGotoDeclarationHandler implements LineMarkerProvider {

    @Nullable
    @Override
    public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        // Handler navigation to logic files
        if (element instanceof HandlerValueNode) {
            return createNavigationMarkerForHandler(element, (HandlerValueNode) element);
        }

        // Check if this is the "middleware" keyword in @server annotation
        if (isMiddlewareKeyword(element)) {
            return createNavigationMarkerForMiddlewareKeyword(element);
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

        // Check if the logic file exists before showing the marker
        if (!logicFileExists(element, handlerName)) {
            return null;
        }

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
            // Navigate to NewHandler function
            navigateToNewHandlerFunction(sourceElement.getProject(), targetFile, handlerName);
        }
    }

    @Nullable
    private PsiFile findLogicFile(@NotNull Project project, @NotNull String targetPath, @NotNull String handlerName, @NotNull PsiElement sourceElement) {
        // Get the base path from the api/proto file
        VirtualFile sourceFile = sourceElement.getContainingFile().getVirtualFile();
        if (sourceFile == null) {
            return null;
        }

        // Calculate the base path by replacing "desc/api" or "desc/proto" with "internal/logic"
        String filePath = sourceFile.getPath();
        String basePath = filePath;

        // Replace "desc/api" or "api" with "internal/logic"
        if (filePath.contains("/desc/api/")) {
            basePath = filePath.substring(0, filePath.indexOf("/desc/api/")) + "/internal/logic";
        } else if (filePath.contains("/api/")) {
            basePath = filePath.substring(0, filePath.indexOf("/api/")) + "/internal/logic";
        } else if (filePath.contains("/desc/proto/")) {
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

    private void navigateToNewHandlerFunction(@NotNull Project project, @NotNull PsiFile goFile, @NotNull String handlerName) {
        String content = goFile.getText();
        // Capitalize first letter for function name (e.g., "test" -> "Test")
        String functionName = "New" + handlerName.substring(0, 1).toUpperCase() + handlerName.substring(1);
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

    private LineMarkerInfo<?> createNavigationMarkerForMiddlewareKeyword(@NotNull PsiElement element) {
        return new LineMarkerInfo<>(
                element,
                element.getTextRange(),
                ApiIcon.FILE,
                e -> "Navigate to Middleware",
                (e, elt) -> showMiddlewarePopup(elt),
                GutterIconRenderer.Alignment.LEFT,
                () -> "Choose middleware to navigate"
        );
    }

    private void showMiddlewarePopup(@NotNull PsiElement sourceElement) {
        // Extract middleware names from the annotation
        List<String> middlewareNames = extractMiddlewareNames(sourceElement);

        if (middlewareNames.isEmpty()) {
            System.out.println("No middleware names found");
            return;
        }

        System.out.println("Found middleware names: " + middlewareNames);

        // Create action group with middleware names
        DefaultActionGroup actionGroup = new DefaultActionGroup();
        for (String name : middlewareNames) {
            final String middlewareName = name;
            actionGroup.add(new AnAction(name) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    navigateToMiddlewareFile(sourceElement, middlewareName);
                }
            });
        }

        // Show popup at the gutter icon position
        Editor editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(sourceElement.getProject()).getSelectedTextEditor();
        if (editor != null) {
            // Get line number of the middleware element
            int lineNumber = sourceElement.getContainingFile().getViewProvider().getDocument().getLineNumber(sourceElement.getTextRange().getStartOffset());

            // Get the text area position for the first column of the line
            com.intellij.openapi.editor.LogicalPosition logicalPos = new com.intellij.openapi.editor.LogicalPosition(lineNumber, 0);
            java.awt.Point point = editor.logicalPositionToXY(logicalPos);

            // Position the popup at the text area's left edge (which is right after the gutter)
            // The point.x is already relative to the editor component and starts after the gutter
            RelativePoint popupPosition = new RelativePoint(editor.getContentComponent(), point);

            JBPopupFactory.getInstance()
                .createActionGroupPopup(
                    "Choose Middleware",
                    actionGroup,
                    (String dataId) -> null,
                    JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                    false
                )
                .show(popupPosition);
        } else {
            JBPopupFactory.getInstance()
                .createActionGroupPopup(
                    "Choose Middleware",
                    actionGroup,
                    (String dataId) -> null,
                    JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                    false
                )
                .showCenteredInCurrentWindow(sourceElement.getProject());
        }
    }

    private List<String> extractMiddlewareNames(@NotNull PsiElement element) {
        List<String> names = new ArrayList<>();

        // Find the @server annotation by looking at the element containing file
        PsiFile file = element.getContainingFile();
        if (file == null) {
            return names;
        }

        String fileText = file.getText();
        int elementOffset = element.getTextRange().getStartOffset();

        // Search backwards from the middleware keyword to find @server
        int serverStart = fileText.lastIndexOf("@server", elementOffset);
        if (serverStart == -1) {
            return names;
        }

        // Find the end of the annotation block (usually before 'service' keyword)
        int annotationEnd = fileText.indexOf("service", serverStart);
        if (annotationEnd == -1) {
            annotationEnd = fileText.length();
        }

        // Extract the annotation block
        String annotationBlock = fileText.substring(serverStart, annotationEnd);

        // Parse middleware values from the annotation
        String[] lines = annotationBlock.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("middleware:")) {
                // Get the value after "middleware:"
                String value = line.substring("middleware:".length()).trim();
                // Remove quotes if present
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                // Split by comma and add to list
                String[] parts = value.split(",");
                for (String part : parts) {
                    String name = part.trim();
                    if (!name.isEmpty()) {
                        names.add(name);
                    }
                }
            }
        }

        return names;
    }

    private void navigateToMiddlewareFile(@NotNull PsiElement sourceElement, @NotNull String middlewareName) {
        // Get naming style from .jzero.yaml configuration
        String namingFormat = JzeroConfigReader.getNamingStyle(sourceElement.getProject(), sourceElement.getContainingFile());

        // Format the middleware name according to jzero configuration
        String formattedMiddlewareName = JzeroConfigReader.formatFileName(namingFormat, middlewareName);

        // Navigate to middleware files: internal/middleware/{formatted_name}middleware.go
        String targetPath = "internal/middleware/" + formattedMiddlewareName + "middleware.go";

        // Find and navigate to the target middleware file
        PsiFile targetFile = findMiddlewareFile(sourceElement, targetPath);
        if (targetFile != null) {
            // Navigate to the middleware function
            navigateToMiddlewareFunction(sourceElement.getProject(), targetFile, middlewareName);
        }
    }

    @Nullable
    private PsiFile findMiddlewareFile(@NotNull PsiElement sourceElement, @NotNull String targetPath) {
        Project project = sourceElement.getProject();

        // Get the base path from the api file
        VirtualFile sourceFile = sourceElement.getContainingFile().getVirtualFile();
        if (sourceFile == null) {
            return null;
        }

        // Calculate the base path by replacing "desc/api" or "api" with "internal/middleware"
        String filePath = sourceFile.getPath();
        String basePath = filePath;

        if (filePath.contains("/desc/api/")) {
            basePath = filePath.substring(0, filePath.indexOf("/desc/api/")) + "/internal/middleware";
        } else if (filePath.contains("/api/")) {
            basePath = filePath.substring(0, filePath.indexOf("/api/")) + "/internal/middleware";
        }

        // Combine base path with target path (which already contains the relative part)
        String fullPath = basePath + "/" + targetPath.substring("internal/middleware".length());

        // Try to find the file at the calculated path
        VirtualFile targetVirtualFile = sourceFile.getFileSystem().findFileByPath(fullPath);
        if (targetVirtualFile != null) {
            return PsiManager.getInstance(project).findFile(targetVirtualFile);
        }

        return null;
    }

    private void navigateToMiddlewareFunction(@NotNull Project project, @NotNull PsiFile goFile, @NotNull String middlewareName) {
        String content = goFile.getText();

        // Try to find the middleware function
        String[] possiblePatterns = {
            "func " + middlewareName + "(",
            "func New" + middlewareName + "(",
            "func " + middlewareName.substring(0, 1).toUpperCase() + middlewareName.substring(1) + "("
        };

        for (String pattern : possiblePatterns) {
            int functionIndex = content.indexOf(pattern);
            if (functionIndex != -1) {
                openFileAndNavigate(project, goFile.getVirtualFile(), functionIndex);
                return;
            }
        }

        // Fallback to file beginning if function not found
        openFileAndNavigate(project, goFile.getVirtualFile(), 0);
    }

    private boolean isMiddlewareKeyword(@NotNull PsiElement element) {
        String text = element.getText();
        System.out.println("Checking element: '" + text + "'");

        // Only match the exact "middleware" keyword (the key, not the values)
        if (text == null || !text.equals("middleware")) {
            return false;
        }

        System.out.println("Element text is 'middleware', checking siblings...");

        // Check if the next sibling is a colon - this indicates it's the key
        PsiElement nextSibling = element.getNextSibling();
        if (nextSibling != null) {
            String nextText = nextSibling.getText();
            System.out.println("Next sibling: '" + nextText + "'");
            // The key should be followed by colon (possibly with whitespace)
            if (nextText.trim().startsWith(":")) {
                System.out.println("Found colon after middleware, checking @server...");
                // Navigate up to check if this is inside a @server annotation
                PsiElement current = element.getParent();
                while (current != null) {
                    String parentText = current.getText();
                    if (parentText != null && parentText.contains("@server")) {
                        System.out.println("Found @server annotation, returning true");
                        return true;
                    }
                    // Don't go too far up - stop at service level
                    if (current instanceof ServiceNode) {
                        break;
                    }
                    current = current.getParent();
                }
            }
        }

        System.out.println("Not a middleware keyword");
        return false;
    }

    private boolean logicFileExists(@NotNull PsiElement element, @NotNull String handlerName) {
        // Remove "Handler" suffix if present
        if (handlerName.endsWith("Handler")) {
            handlerName = handlerName.substring(0, handlerName.length() - "Handler".length());
        }

        // Find service information
        ServiceInfo serviceInfo = findServiceInfo(element);

        // Calculate target path for logic files
        String targetPath;
        if (serviceInfo != null && serviceInfo.groupName != null) {
            // Get naming style from .jzero.yaml configuration
            String namingFormat = JzeroConfigReader.getNamingStyle(element.getProject(), element.getContainingFile());

            // Format the handler name according to jzero configuration
            String formattedHandlerName = JzeroConfigReader.formatFileName(namingFormat, handlerName);

            // Navigate to logic files
            targetPath = "internal/logic/" + serviceInfo.groupName + "/" + formattedHandlerName + ".go";
        } else {
            // Fallback to logic without group
            String namingFormat = JzeroConfigReader.getNamingStyle(element.getProject(), element.getContainingFile());
            String formattedHandlerName = JzeroConfigReader.formatFileName(namingFormat, handlerName);
            targetPath = "internal/logic/" + formattedHandlerName + ".go";
        }

        return findLogicFile(element.getProject(), targetPath, handlerName, element) != null;
    }

    private static class ServiceInfo {
        String groupName;
    }
}
