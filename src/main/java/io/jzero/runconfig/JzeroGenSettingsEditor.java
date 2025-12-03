package io.jzero.runconfig;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Settings editor for jzero gen run configuration
 */
public class JzeroGenSettingsEditor extends SettingsEditor<JzeroGenRunConfiguration> {

    private final JPanel panel;
    private final JBTextField commandField = new JBTextField();
    private final JBTextField workingDirectoryField = new JBTextField();

    public JzeroGenSettingsEditor() {
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(new JBLabel("Command:"), commandField, 1, false)
            .addLabeledComponent(new JBLabel("Working Directory:"), workingDirectoryField, 1, false)
            .addComponentFillVertically(new JPanel(), 0)
            .getPanel();
    }

    @Override
    protected void resetEditorFrom(@NotNull JzeroGenRunConfiguration configuration) {
        commandField.setText(configuration.getCommand());
        workingDirectoryField.setText(configuration.getWorkingDirectory());
    }

    @Override
    protected void applyEditorTo(@NotNull JzeroGenRunConfiguration configuration)
        throws ConfigurationException {
        configuration.setCommand(commandField.getText());
        configuration.setWorkingDirectory(workingDirectoryField.getText());
    }

    @Override
    protected @NotNull JComponent createEditor() {
        return panel;
    }
}
