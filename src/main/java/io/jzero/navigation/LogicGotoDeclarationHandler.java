package io.jzero.navigation;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import io.jzero.icon.ApiIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * LineMarker provider for logic file navigation to api/proto files
 * Navigates from internal/logic files back to their api/proto definitions
 * Uses metadata.json to find the corresponding api/proto file and line number
 */
public class LogicGotoDeclarationHandler implements LineMarkerProvider {

    @Nullable
    @Override
    public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        // Check if this is a Go file
        PsiFile containingFile = element.getContainingFile();
        if (containingFile == null || !containingFile.getName().endsWith(".go")) {
            return null;
        }

        // Check if the file is in internal/logic directory
        VirtualFile virtualFile = containingFile.getVirtualFile();
        if (virtualFile == null) {
            return null;
        }

        String filePath = virtualFile.getPath();
        if (!filePath.contains("/internal/logic/") && !filePath.contains("\\internal\\logic\\")) {
            return null;
        }

        // Check if this element is a function declaration starting with "New"
        if (!isNewFunctionDeclaration(element)) {
            return null;
        }

        // Check if metadata.json exists and has matching entry for this logic file
        if (!hasMetadataForLogicFile(filePath)) {
            return null;
        }

        // Found a "func NewXxx" pattern with valid metadata, show navigation icon
        return createNavigationMarker(element);
    }

    private LineMarkerInfo<?> createNavigationMarker(@NotNull PsiElement element) {
        return new LineMarkerInfo<>(
                element,
                element.getTextRange(),
                ApiIcon.FILE,
                e -> "Navigate to API/Proto definition",
                (e, elt) -> navigateToDescFile(elt),
                GutterIconRenderer.Alignment.LEFT,
                () -> "Go to API/Proto"
        );
    }

    private void navigateToDescFile(@NotNull PsiElement sourceElement) {
        PsiFile containingFile = sourceElement.getContainingFile();
        if (containingFile == null) {
            return;
        }

        VirtualFile sourceFile = containingFile.getVirtualFile();
        if (sourceFile == null) {
            return;
        }

        String filePath = sourceFile.getPath();

        // Find all metadata entries for this logic file
        List<LogicMetadata> metadataList = findAllMetadataForLogicFile(filePath);

        if (metadataList.isEmpty()) {
            return;
        }

        // If multiple entries found, we could show a popup to choose
        // For now, just navigate to the first one
        LogicMetadata metadata = metadataList.get(0);

        VirtualFile descFile = resolveDescFile(sourceElement, metadata.descPath);
        if (descFile != null) {
            Project project = sourceElement.getProject();
            openFileAndNavigate(project, descFile, metadata.descLine);
        }
    }

    @NotNull
    private List<LogicMetadata> findAllMetadataForLogicFile(@NotNull String logicFilePath) {
        List<LogicMetadata> results = new ArrayList<>();

        try {
            String basePath = extractBasePath(logicFilePath);
            if (basePath == null) {
                return results;
            }

            String homeDir = System.getProperty("user.home");
            String metadataPath = homeDir + "/.jzero/desc-metadata" + basePath + "/metadata.json";

            File metadataFile = new File(metadataPath);
            if (!metadataFile.exists()) {
                return results;
            }

            String content = new String(Files.readAllBytes(Paths.get(metadataPath)));
            return parseAllMetadataForFile(content, logicFilePath);

        } catch (IOException e) {
            e.printStackTrace();
        }

        return results;
    }

    @NotNull
    private List<LogicMetadata> parseAllMetadataForFile(@NotNull String jsonContent, @NotNull String logicFilePath) {
        List<LogicMetadata> results = new ArrayList<>();

        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(jsonContent, JsonObject.class);

            if (root == null) {
                return results;
            }

            String normalizedTargetPath = logicFilePath.replace("\\", "/");

            // First try "api.routes" if desc file exists
            if (root.has("api")) {
                JsonObject api = root.getAsJsonObject("api");
                if (api.has("routes")) {
                    JsonArray entries = api.getAsJsonArray("routes");

                    for (int i = 0; i < entries.size(); i++) {
                        JsonObject entry = entries.get(i).getAsJsonObject();

                        if (!entry.has("logic")) {
                            continue;
                        }

                        // Logic field is absolute path
                        String entryLogicPath = entry.get("logic").getAsString();
                        String normalizedEntryPath = entryLogicPath.replace("\\", "/");

                        if (normalizedEntryPath.equals(normalizedTargetPath)) {
                            if (entry.has("desc")) {
                                String descPath = entry.get("desc").getAsString();
                                int descLine = entry.has("desc-line") ? entry.get("desc-line").getAsInt() : 0;

                                LogicMetadata metadata = new LogicMetadata();
                                metadata.descPath = descPath;
                                metadata.descLine = descLine;
                                results.add(metadata);
                            }
                        }
                    }
                }
            }

            // If no results from api.routes or api doesn't exist, try proto.rpcs
            if (root.has("proto")) {
                JsonObject proto = root.getAsJsonObject("proto");
                if (proto.has("rpcs")) {
                    JsonArray entries = proto.getAsJsonArray("rpcs");

                    for (int i = 0; i < entries.size(); i++) {
                        JsonObject entry = entries.get(i).getAsJsonObject();

                        if (!entry.has("logic")) {
                            continue;
                        }

                        // Logic field is absolute path
                        String entryLogicPath = entry.get("logic").getAsString();
                        String normalizedEntryPath = entryLogicPath.replace("\\", "/");

                        if (normalizedEntryPath.equals(normalizedTargetPath)) {
                            if (entry.has("desc")) {
                                String descPath = entry.get("desc").getAsString();
                                int descLine = entry.has("desc-line") ? entry.get("desc-line").getAsInt() : 0;

                                LogicMetadata metadata = new LogicMetadata();
                                metadata.descPath = descPath;
                                metadata.descLine = descLine;
                                results.add(metadata);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return results;
    }

    @Nullable
    private String extractBasePath(@NotNull String logicFilePath) {
        int logicIndex = logicFilePath.indexOf("/internal/logic/");
        if (logicIndex == -1) {
            logicIndex = logicFilePath.indexOf("\\internal\\logic\\");
            if (logicIndex == -1) {
                return null;
            }
        }

        return logicFilePath.substring(0, logicIndex);
    }

    @Nullable
    private VirtualFile resolveDescFile(@NotNull PsiElement sourceElement,
                                       @NotNull String descPath) {
        // Desc path is always absolute in metadata.json
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(descPath);
        if (file != null && file.exists()) {
            return file;
        }

        return null;
    }

    private void openFileAndNavigate(@NotNull Project project, @NotNull VirtualFile file, int lineNumber) {
        com.intellij.openapi.fileEditor.OpenFileDescriptor descriptor =
            new com.intellij.openapi.fileEditor.OpenFileDescriptor(
                project,
                file,
                lineNumber > 0 ? lineNumber - 1 : 0, // Line numbers are 0-indexed
                0
            );
        descriptor.navigate(true);
    }

    @Override
    public void collectSlowLineMarkers(@NotNull java.util.List<? extends PsiElement> elements,
                                       @NotNull java.util.Collection<? super LineMarkerInfo<?>> result) {
        // Not needed for this implementation
    }

    /**
     * Check if metadata.json exists and contains entry for this logic file
     */
    private boolean hasMetadataForLogicFile(@NotNull String logicFilePath) {
        try {
            String basePath = extractBasePath(logicFilePath);
            if (basePath == null) {
                return false;
            }

            String homeDir = System.getProperty("user.home");
            String metadataPath = homeDir + "/.jzero/desc-metadata" + basePath + "/metadata.json";

            File metadataFile = new File(metadataPath);
            if (!metadataFile.exists()) {
                return false;
            }

            // Parse and check if there's a matching entry
            String content = new String(Files.readAllBytes(Paths.get(metadataPath)));
            List<LogicMetadata> metadataList = parseAllMetadataForFile(content, logicFilePath);

            return !metadataList.isEmpty();

        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Check if the given element is a function declaration starting with "New"
     * Matches patterns like: func NewCreateUser(...) or func NewGetUser(...)
     */
    private boolean isNewFunctionDeclaration(@NotNull PsiElement element) {
        String elementText = element.getText();

        // Check if current element text looks like a function name
        if (elementText == null || !elementText.matches("[a-zA-Z][a-zA-Z0-9_]*")) {
            return false;
        }

        // Check if this function name starts with "New"
        if (!elementText.startsWith("New")) {
            return false;
        }

        // Check if the previous sibling is "func" keyword
        PsiElement prev = element.getPrevSibling();
        while (prev != null && (prev.getText() == null || prev.getText().trim().isEmpty())) {
            prev = prev.getPrevSibling();
        }

        return prev != null && "func".equals(prev.getText().trim());
    }

    private static class LogicMetadata {
        String descPath;
        int descLine;
    }
}
