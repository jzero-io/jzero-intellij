package io.jzero.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Jzero Tool Window Factory - 创建 Jzero 工具窗口
 */
public class JzeroToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JzeroToolWindow jzeroToolWindow = new JzeroToolWindow(project);
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(jzeroToolWindow.getComponent(), "", false);
        toolWindow.getContentManager().addContent(content);
    }
}