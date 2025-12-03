package io.jzero.runconfig;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for creating jzero gen run configurations
 */
public class JzeroGenConfigurationFactory extends ConfigurationFactory {

    public JzeroGenConfigurationFactory(@NotNull ConfigurationType type) {
        super(type);
    }

    @Override
    public @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project) {
        return new JzeroGenRunConfiguration(project, this, "Jzero Gen");
    }

    @Override
    public @NotNull String getId() {
        return JzeroGenConfigurationType.ID;
    }
}
