package io.jzero.runconfig;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Configuration type for jzero gen command execution
 */
public class JzeroGenConfigurationType implements ConfigurationType {

    public static final String ID = "JzeroGenConfiguration";

    @Override
    public @NotNull String getDisplayName() {
        return "Jzero Gen";
    }

    @Override
    public @Nls String getConfigurationTypeDescription() {
        return "Jzero gen command execution configuration";
    }

    @Override
    public Icon getIcon() {
        return AllIcons.Actions.Execute;
    }

    @Override
    public @NotNull String getId() {
        return ID;
    }

    @Override
    public ConfigurationFactory[] getConfigurationFactories() {
        return new ConfigurationFactory[]{new JzeroGenConfigurationFactory(this)};
    }
}
