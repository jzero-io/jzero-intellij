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
        // Disable line markers - Ctrl+Click navigation is handled by ApiGotoDeclarationHandler
        return null;
    }
}