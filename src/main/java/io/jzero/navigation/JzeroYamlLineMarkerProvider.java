package io.jzero.navigation;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.jzero.panel.JzeroOutputPanel;
import io.jzero.util.ExecWithOutput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.List;

public class JzeroYamlLineMarkerProvider implements LineMarkerProvider {

    @Nullable
    @Override
    public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        PsiFile containingFile = element.getContainingFile();
        if (containingFile == null) {
            return null;
        }

        VirtualFile virtualFile = containingFile.getVirtualFile();
        if (virtualFile == null || !".jzero.yaml".equals(virtualFile.getName())) {
            return null;
        }

        // Check if the element contains the "gen" keyword
        String elementText = element.getText();
        if (elementText == null || !elementText.trim().equals("gen")) {
            return null;
        }

        Project project = containingFile.getProject();
        String configDir = virtualFile.getParent() != null ? virtualFile.getParent().getPath() : "";

        return new LineMarkerInfo<>(
                element,
                element.getTextRange(),
                AllIcons.Actions.Execute,
                psiElement -> "Execute jzero gen",
                new GutterIconNavigationHandler<PsiElement>() {
                    @Override
                    public void navigate(MouseEvent e, PsiElement elt) {
                        // Create and show output panel on EDT first
                        ApplicationManager.getApplication().invokeLater(() -> {
                            JzeroOutputPanel outputPanel = new JzeroOutputPanel(project);
                            outputPanel.clear();
                            outputPanel.printMessage("Preparing to execute jzero gen command...\n\n");

                            // Then run the command in background
                            ProgressManager.getInstance().run(new Task.Backgroundable(project, "Running jzero gen...") {
                                @Override
                                public void run(@NotNull ProgressIndicator indicator) {
                                    indicator.setIndeterminate(true);
                                    indicator.setText("Executing jzero gen command...");

                                    // Update UI on EDT
                                    ApplicationManager.getApplication().invokeLater(() -> {
                                        outputPanel.printMessage("Executing jzero gen command...\n");
                                    });

                                    String cmd = "jzero gen";
                                    ExecWithOutput.ExecutionResult result = ExecWithOutput.executeCommand(
                                        outputPanel, configDir, cmd
                                    );
                                }
                            });
                        });
                    }
                },
                GutterIconRenderer.Alignment.LEFT
        );
    }

    @Override
    public void collectSlowLineMarkers(@NotNull List<? extends PsiElement> elements, @NotNull Collection<? super LineMarkerInfo<?>> result) {
        // Not needed for this implementation
    }
}