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
import io.jzero.psi.nodes.StructNameNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * LineMarker provider for type navigation to types.go files
 * Based on ApiNavigationLineMarkerProvider pattern - only targets specific PSI node types
 */
public class TypeGotoDeclarationHandler implements LineMarkerProvider {

    @Nullable
    @Override
    public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        // Only look for struct name nodes for navigation to types.go files
        if (element instanceof StructNameNode) {
            return createNavigationMarkerForStruct(element, (StructNameNode) element);
        }

        return null;
    }

    private LineMarkerInfo<?> createNavigationMarkerForStruct(@NotNull PsiElement element, @NotNull StructNameNode structNode) {
        String structName = element.getText();
        if (structName == null || structName.isEmpty()) {
            return null;
        }

        // Check if the target types file exists before showing the marker
        if (!typesFileExists(element)) {
            return null;
        }

        return new LineMarkerInfo<>(
                element,
                element.getTextRange(),
                ApiIcon.FILE,
                e -> "Navigate to Go Types: " + structName,
                (e, elt) -> navigateToTypesFile(elt, structName),
                GutterIconRenderer.Alignment.LEFT,
                () -> "Go to " + structName + " struct"
        );
    }

    private void navigateToTypesFile(@NotNull PsiElement sourceElement, @NotNull String structName) {
        // Get current API file from the clicked element
        PsiFile currentApiFile = sourceElement.getContainingFile();
        if (currentApiFile == null) {
            return;
        }

        Project project = currentApiFile.getProject();
        String goPackage = extractGoPackageFromApiFile(currentApiFile);

        PsiFile targetGoFile;
        if (goPackage != null && !goPackage.isEmpty()) {
            // Use smart path mapping based on go_package and source file path
            targetGoFile = findTypesGoFile(sourceElement, goPackage);
        } else {
            // No go_package found, look for internal/types/types.go
            targetGoFile = findRootTypesGoFile(sourceElement);
        }

        if (targetGoFile != null) {
            // Found the Go file, navigate to struct definition
            navigateToStructDefinition(project, targetGoFile, structName);
        }
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
    private PsiFile findTypesGoFile(@NotNull PsiElement sourceElement, @NotNull String packagePath) {
        Project project = sourceElement.getProject();

        // Get the base path from the api file
        VirtualFile sourceFile = sourceElement.getContainingFile().getVirtualFile();
        if (sourceFile == null) {
            return null;
        }

        // Calculate the base path by replacing "desc/api" or "api" with "internal/types"
        String filePath = sourceFile.getPath();
        String basePath = filePath;

        if (filePath.contains("/desc/api/")) {
            basePath = filePath.substring(0, filePath.indexOf("/desc/api/")) + "/internal/types";
        } else if (filePath.contains("/api/")) {
            basePath = filePath.substring(0, filePath.indexOf("/api/")) + "/internal/types";
        }

        // Combine base path with package path to get full path
        String fullPath;
        if (packagePath.isEmpty()) {
            fullPath = basePath + "/types.go";
        } else {
            fullPath = basePath + "/" + packagePath + "/types.go";
        }

        // Try to find the file at the calculated path
        VirtualFile targetVirtualFile = sourceFile.getFileSystem().findFileByPath(fullPath);
        if (targetVirtualFile != null) {
            return PsiManager.getInstance(project).findFile(targetVirtualFile);
        }

        return null;
    }

    @Nullable
    private PsiFile findRootTypesGoFile(@NotNull PsiElement sourceElement) {
        Project project = sourceElement.getProject();

        // Get the base path from the api file
        VirtualFile sourceFile = sourceElement.getContainingFile().getVirtualFile();
        if (sourceFile == null) {
            return null;
        }

        // Calculate the base path by replacing "desc/api" or "api" with "internal/types"
        String filePath = sourceFile.getPath();
        String basePath = filePath;

        if (filePath.contains("/desc/api/")) {
            basePath = filePath.substring(0, filePath.indexOf("/desc/api/")) + "/internal/types";
        } else if (filePath.contains("/api/")) {
            basePath = filePath.substring(0, filePath.indexOf("/api/")) + "/internal/types";
        }

        // Full path to internal/types/types.go
        String fullPath = basePath + "/types.go";

        // Try to find the file at the calculated path
        VirtualFile targetVirtualFile = sourceFile.getFileSystem().findFileByPath(fullPath);
        if (targetVirtualFile != null) {
            return PsiManager.getInstance(project).findFile(targetVirtualFile);
        }

        return null;
    }

    private void navigateToStructDefinition(@NotNull Project project, @NotNull PsiFile goFile, @NotNull String structName) {
        String content = goFile.getText();
        // Search for "type StructName struct" pattern using string operations
        String structSearch = "type " + structName + " struct";
        int structIndex = content.indexOf(structSearch);
        if (structIndex != -1) {
            openFileAndNavigate(project, goFile.getVirtualFile(), structIndex);
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

    @Override
    public void collectSlowLineMarkers(@NotNull java.util.List<? extends PsiElement> elements, @NotNull java.util.Collection<? super LineMarkerInfo<?>> result) {
        // Not needed for this implementation
    }

    private boolean typesFileExists(@NotNull PsiElement element) {
        // Get current API file from the element
        PsiFile currentApiFile = element.getContainingFile();
        if (currentApiFile == null) {
            return false;
        }

        String goPackage = extractGoPackageFromApiFile(currentApiFile);

        PsiFile targetGoFile;
        if (goPackage != null && !goPackage.isEmpty()) {
            // Use smart path mapping based on go_package and source file path
            targetGoFile = findTypesGoFile(element, goPackage);
        } else {
            // No go_package found, look for internal/types/types.go
            targetGoFile = findRootTypesGoFile(element);
        }

        return targetGoFile != null;
    }
}