package io.jzero.panel;

import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import io.jzero.util.ExecWithOutput;

/**
 * Output panel for displaying jzero gen command execution results
 */
public class JzeroOutputPanel implements Disposable {

    private static final String TOOL_WINDOW_ID = "Jzero Output";

    private final Project project;
    private ConsoleView consoleView;
    private JPanel mainPanel;
    private JPanel buttonPanel;
    private JButton cancelButton;
    private boolean disposed = false;
    private ExecWithOutput.CancellationToken currentToken;

    public JzeroOutputPanel(@NotNull Project project) {
        this.project = project;
        createUI();
    }

    private void createUI() {
        mainPanel = new JPanel(new BorderLayout());

        // Create console view for output
        consoleView = (ConsoleViewImpl) TextConsoleBuilderFactory.getInstance()
                .createBuilder(project)
                .getConsole();

        mainPanel.add(consoleView.getComponent(), BorderLayout.CENTER);

        // Create button panel
        createButtonPanel();

        // Add to tool window
        addToToolWindow();
    }

    private void createButtonPanel() {
        buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        cancelButton = new JButton("Cancel");
        cancelButton.setEnabled(false);
        cancelButton.setToolTipText("Cancel running jzero gen command");

        cancelButton.addActionListener(e -> {
            if (currentToken != null) {
                currentToken.cancel();
                cancelButton.setEnabled(false);
                printMessage("Cancelling command...\n");
            }
        });

        buttonPanel.add(cancelButton);
        mainPanel.add(buttonPanel, BorderLayout.NORTH);
    }

    private void addToToolWindow() {
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        ToolWindow toolWindow = toolWindowManager.getToolWindow(TOOL_WINDOW_ID);

        if (toolWindow == null) {
            // Create tool window if it doesn't exist
            toolWindow = toolWindowManager.registerToolWindow(TOOL_WINDOW_ID, true, ToolWindowAnchor.BOTTOM);
        }

        // Remove old content if exists
        Content oldContent = toolWindow.getContentManager().findContent("jzero gen");
        if (oldContent != null) {
            toolWindow.getContentManager().removeContent(oldContent, true);
        }

        // Add new content
        Content content = ContentFactory.getInstance().createContent(mainPanel, "jzero gen", false);
        toolWindow.getContentManager().addContent(content);
        toolWindow.show(null);
    }

    public void printMessage(@NotNull String message) {
        if (!disposed && consoleView != null) {
            consoleView.print(message, ConsoleViewContentType.NORMAL_OUTPUT);
        }
    }

    public void printError(@NotNull String message) {
        if (!disposed && consoleView != null) {
            consoleView.print(message, ConsoleViewContentType.ERROR_OUTPUT);
        }
    }

    public void printCommand(@NotNull String command) {
        if (!disposed && consoleView != null) {
            consoleView.print("$ " + command + "\n", ConsoleViewContentType.USER_INPUT);
        }
    }

    public void clear() {
        if (!disposed && consoleView != null) {
            consoleView.clear();
        }
    }

    public void show() {
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        ToolWindow toolWindow = toolWindowManager.getToolWindow(TOOL_WINDOW_ID);
        if (toolWindow != null) {
            toolWindow.show(null);
        }
    }

    /**
     * Set up the panel for command execution
     * @param token Cancellation token for this execution
     */
    public void startExecution(@NotNull ExecWithOutput.CancellationToken token) {
        this.currentToken = token;
        if (cancelButton != null) {
            cancelButton.setEnabled(true);
        }
    }

    /**
     * Called when command execution is complete (whether successful, failed, or cancelled)
     */
    public void finishExecution() {
        this.currentToken = null;
        if (cancelButton != null) {
            cancelButton.setEnabled(false);
        }
    }

    @Override
    public void dispose() {
        if (!disposed) {
            disposed = true;
            if (consoleView != null) {
                Disposer.dispose(consoleView);
            }
            if (mainPanel != null) {
                mainPanel.removeAll();
            }
        }
    }
}