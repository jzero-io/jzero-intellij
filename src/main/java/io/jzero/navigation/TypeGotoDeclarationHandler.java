package io.jzero.navigation;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
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

        return new LineMarkerInfo<>(
                element,
                element.getTextRange(),
                AllIcons.Actions.Find,
                e -> "Navigate to Types: " + structName,
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

        PsiFile targetGoFile = null;
        if (goPackage != null && !goPackage.isEmpty()) {
            // Use smart path mapping based on go_package
            targetGoFile = findTypesGoFile(project, goPackage);
        } else {
            // No go_package found, look for types/types.go
            targetGoFile = findRootTypesGoFile(project);
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
    private PsiFile findTypesGoFile(@NotNull Project project, @NotNull String packagePath) {
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
    private PsiFile findRootTypesGoFile(@NotNull Project project) {
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
}