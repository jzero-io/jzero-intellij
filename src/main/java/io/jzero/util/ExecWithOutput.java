package io.jzero.util;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import io.jzero.panel.JzeroOutputPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Utility class for executing commands with real-time output display
 */
public class ExecWithOutput {

    public static class ExecutionResult {
        private final int exitCode;
        private final boolean success;

        public ExecutionResult(int exitCode, boolean success) {
            this.exitCode = exitCode;
            this.success = success;
        }

        public int getExitCode() {
            return exitCode;
        }

        public boolean isSuccess() {
            return success;
        }
    }

    /**
     * Execute a command with real-time output to the console panel
     * @param outputPanel The output panel to display results
     * @param workingDirectory The working directory for command execution
     * @param command The command to execute
     * @return ExecutionResult containing exit code and success status
     */
    @NotNull
    public static ExecutionResult executeCommand(@NotNull JzeroOutputPanel outputPanel,
                                                 @Nullable String workingDirectory,
                                                 @NotNull String command) {
        try {
            // Prepare command
            GeneralCommandLine commandLine = prepareCommandLine(command);

            if (workingDirectory != null && !workingDirectory.trim().isEmpty()) {
                commandLine.setWorkDirectory(workingDirectory);
            }

            // Print the command being executed
            outputPanel.printCommand(command);

            // Execute the command with real-time output
            ProcessHandler processHandler = new com.intellij.execution.process.OSProcessHandler(commandLine);

            // Add process listener for real-time output
            processHandler.addProcessListener(new ProcessAdapter() {
                @Override
                public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
                    String text = event.getText();
                    if (!StringUtil.isEmptyOrSpaces(text)) {
                        // Ensure UI operations are on EDT
                        ApplicationManager.getApplication().invokeLater(() -> {
                            if (outputType == ProcessOutputType.STDOUT) {
                                outputPanel.printMessage(text);
                            } else if (outputType == ProcessOutputType.STDERR) {
                                outputPanel.printError(text);
                            }
                        });
                    }
                }
            });

            // Start the process
            processHandler.startNotify();

            // Wait for completion
            while (!processHandler.isProcessTerminated()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            int exitCode = processHandler.getExitCode() != null ? processHandler.getExitCode() : -1;
            boolean success = exitCode == 0;

            // Print completion message on EDT
            ApplicationManager.getApplication().invokeLater(() -> {
                if (success) {
                    outputPanel.printMessage("\n✓ Process completed successfully with exit code " + exitCode + "\n");
                } else {
                    outputPanel.printError("\n✗ Process failed with exit code " + exitCode + "\n");
                }
            });

            return new ExecutionResult(exitCode, success);

        } catch (Exception e) {
            // Print error message on EDT
            ApplicationManager.getApplication().invokeLater(() -> {
                outputPanel.printError("\n✗ Failed to execute command: " + e.getMessage() + "\n");
            });
            return new ExecutionResult(-1, false);
        }
    }

    @NotNull
    private static GeneralCommandLine prepareCommandLine(@NotNull String command) {
        String os = System.getProperty("os.name");
        os = os.toLowerCase();

        if (os.startsWith("mac") || os.startsWith("linux")) {
            String shell = System.getenv("SHELL");
            if (StringUtil.isEmptyOrSpaces(shell)) {
                shell = "/bin/sh";
            }
            return new GeneralCommandLine(shell, "-c", command);
        } else if (os.startsWith("windows")) {
            return new GeneralCommandLine("cmd", "/c", command);
        } else {
            // Default to sh for unknown systems
            return new GeneralCommandLine("/bin/sh", "-c", command);
        }
    }
}