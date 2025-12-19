package io.jzero.navigation;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.List;

/**
 * LineMarkerProvider for API files to add navigation gutter icons for type definitions
 * Shows navigation buttons in the gutter area next to line numbers for API type definitions
 */
public class TypeGotoDeclarationHandler implements LineMarkerProvider {

    
    @Nullable
    @Override
    public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        PsiFile containingFile = element.getContainingFile();
        if (containingFile == null || !containingFile.getName().endsWith(".api")) {
            return null;
        }

        // Only show icons for the "type" keyword in type definitions to avoid multiple arrows
        String elementText = element.getText();
        if (!"type".equals(elementText)) {
            return null;
        }

        // Check if this line contains a type definition pattern: "type TypeName {"
        String typeName = extractTypeNameFromLine(element);
        if (typeName == null) {
            return null;
        }

        final String finalTypeName = typeName;

        // Create navigation handler
        NavigationHandler handler = new NavigationHandler(containingFile.getProject(), finalTypeName, element);

        // Create line marker info with navigation icon
        return new LineMarkerInfo<>(
                element,
                element.getTextRange(),
                AllIcons.Actions.Forward,
                (PsiElement e) -> "Navigate to Go struct for " + finalTypeName,
                handler,
                GutterIconRenderer.Alignment.LEFT
        );
    }

    @Override
    public void collectSlowLineMarkers(@NotNull List<? extends PsiElement> elements, @NotNull Collection<? super LineMarkerInfo<?>> result) {
        // Not needed for this implementation
    }

    @Nullable
    private String extractTypeNameFromLine(@NotNull PsiElement element) {
        String lineText = getLineText(element);
        if (lineText == null) {
            return null;
        }

        // Check if line contains "type" and "{"
        String trimmedLine = lineText.trim();
        if (!trimmedLine.startsWith("type ") || !trimmedLine.contains("{")) {
            return null;
        }

        // Extract type name: "type ManageRole {" -> "ManageRole"
        String[] parts = trimmedLine.split("\\s+");
        if (parts.length >= 2) {
            String potentialName = parts[1];
            // Check if it starts with uppercase letter
            if (potentialName.length() > 0 && Character.isUpperCase(potentialName.charAt(0))) {
                return potentialName;
            }
        }

        return null;
    }

    @Nullable
    private String getLineText(@NotNull PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (file == null) {
            return null;
        }

        int offset = element.getTextOffset();
        String content = file.getText();

        // Find the line containing this element
        int lineStart = offset;
        while (lineStart > 0 && content.charAt(lineStart - 1) != '\n') {
            lineStart--;
        }

        int lineEnd = offset;
        while (lineEnd < content.length() && content.charAt(lineEnd) != '\n') {
            lineEnd++;
        }

        return content.substring(lineStart, lineEnd);
    }

    private static class NavigationHandler implements GutterIconNavigationHandler<PsiElement> {
        private final Project project;
        private final String typeName;
        private final PsiElement sourceElement;

        public NavigationHandler(Project project, String typeName, PsiElement sourceElement) {
            this.project = project;
            this.typeName = typeName;
            this.sourceElement = sourceElement;
        }

        @Override
        public void navigate(MouseEvent e, PsiElement element) {
            // Get current API file and try to extract go_package
            PsiFile currentApiFile = sourceElement.getContainingFile();
            if (currentApiFile == null) {
                return;
            }

            String goPackage = extractGoPackageFromApiFile(currentApiFile);

            PsiFile targetGoFile = null;
            if (goPackage != null && !goPackage.isEmpty()) {
                // Use smart path mapping based on go_package
                targetGoFile = findTypesGoFile(goPackage);
            } else {
                // No go_package found, look for types/types.go
                targetGoFile = findRootTypesGoFile();
            }

            if (targetGoFile != null) {
                // Found the Go file, navigate to struct definition
                navigateToStructDefinition(targetGoFile);
            }
        }

        @Nullable
        private PsiFile findGoFileByGoPackageMapping() {
            // Get current API file path and content
            PsiFile currentApiFile = sourceElement.getContainingFile();
            if (currentApiFile == null) {
                return null;
            }

            // Extract go_package from API file
            String goPackage = extractGoPackageFromApiFile(currentApiFile);
            if (goPackage == null) {
                return null;
            }

            // Find Go types.go file with matching package path
            return findTypesGoFile(goPackage);
        }

        @Nullable
        private String extractGoPackageFromApiFile(@NotNull PsiFile apiFile) {
            String content = apiFile.getText();
            // Extract go_package from API file using string operations
            String[] lines = content.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("go_package:")) {
                    int quoteStart = line.indexOf('"');
                    int quoteEnd = line.lastIndexOf('"');
                    if (quoteStart != -1 && quoteEnd != -1 && quoteEnd > quoteStart + 1) {
                        return line.substring(quoteStart + 1, quoteEnd);
                    }
                }
            }
            return null;
        }

        @Nullable
        private PsiFile findTypesGoFile(@NotNull String packagePath) {
            // Search for .go files in the project
            Collection<VirtualFile> goFiles = FilenameIndex.getAllFilesByExt(project, "go", GlobalSearchScope.projectScope(project));

            for (VirtualFile file : goFiles) {
                String filePath = file.getPath();

                // Look for types.go files with matching package path
                if (filePath.endsWith("types.go") && filePath.contains("/internal/types/")) {
                    String extractedPath = extractPackagePathFromGoFile(filePath);
                    if (packagePath.equals(extractedPath)) {
                        return PsiManager.getInstance(project).findFile(file);
                    }
                }
            }

            return null;
        }

        @Nullable
        private String extractPackagePathFromGoFile(@NotNull String goFilePath) {
            // Example: /project/server/internal/types/v1/manage/role/types.go
            // Extract: v1/manage/role

            int internalTypesIndex = goFilePath.indexOf("/internal/types/");
            if (internalTypesIndex == -1) {
                return null;
            }

            String afterInternalTypes = goFilePath.substring(internalTypesIndex + "/internal/types/".length());

            // Remove filename (types.go)
            int lastSlash = afterInternalTypes.lastIndexOf('/');
            if (lastSlash > 0) {
                return afterInternalTypes.substring(0, lastSlash);
            }

            return null;
        }

        @Nullable
        private PsiFile findRootTypesGoFile() {
            // Search for .go files in the project
            Collection<VirtualFile> goFiles = FilenameIndex.getAllFilesByExt(project, "go", GlobalSearchScope.projectScope(project));

            for (VirtualFile file : goFiles) {
                String filePath = file.getPath();

                // Look for types/types.go file (root types file without package path)
                if (filePath.endsWith("types/types.go")) {
                    return PsiManager.getInstance(project).findFile(file);
                }
            }

            return null;
        }

        private void navigateToStructDefinition(@NotNull PsiFile goFile) {
            String content = goFile.getText();
            // Search for "type TypeName struct" pattern using string operations
            String structSearch = "type " + typeName + " struct";
            int structIndex = content.indexOf(structSearch);
            if (structIndex != -1) {
                openFileAndNavigate(goFile, structIndex);
            }
        }

        private void openFileAndNavigate(@NotNull PsiFile psiFile, int targetOffset) {
            // Open the file first
            FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
            fileEditorManager.openFile(psiFile.getVirtualFile(), true, true);

            // Get the editor
            Editor editor = fileEditorManager.getSelectedTextEditor();
            if (editor != null) {
                // Navigate to the exact offset where the struct is defined
                editor.getCaretModel().moveToOffset(targetOffset);

                // Scroll to make the element visible
                editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
            }
        }
    }
}