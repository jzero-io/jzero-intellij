package io.jzero.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * Utility class for reading jzero configuration from .jzero.yaml files
 */
public class JzeroConfigReader {

    private static final String CONFIG_FILENAME = ".jzero.yaml";
    private static JzeroConfig cachedConfig = null;
    private static String cachedApiPath = null;

    /**
     * Configuration class for jzero YAML structure
     */
    public static class JzeroConfig {
        private GenConfig gen;

        public GenConfig getGen() {
            return gen;
        }

        public void setGen(GenConfig gen) {
            this.gen = gen;
        }
    }

    public static class GenConfig {
        private String style;
        public String getStyle() {
            return style;
        }
        public void setStyle(String style) {
            this.style = style;
        }
    }
    
    /**
     * Get the naming style from configuration with caching
     * @param project The project
     * @param apiFile The API file (can be null)
     * @return Naming style string or default
     */
    @NotNull
    public static String getNamingStyle(@NotNull Project project, @Nullable PsiFile apiFile) {
        String currentApiPath = null;

        // Get API file absolute path
        if (apiFile != null) {
            VirtualFile virtualFile = apiFile.getVirtualFile();
            if (virtualFile != null) {
                currentApiPath = virtualFile.getPath();
            }
        }

        // Clear cache if API file path changed
        if (currentApiPath == null || !currentApiPath.equals(cachedApiPath)) {
            cachedConfig = null;
            cachedApiPath = currentApiPath;
        }

        // Load config if not cached
        if (cachedConfig == null) {
            cachedConfig = loadConfig(project, apiFile);
        }

        if (cachedConfig != null && cachedConfig.getGen() != null) {
            String style = cachedConfig.getGen().getStyle();
            if (style != null && !style.trim().isEmpty()) {
                return style.trim();
            }
        }

        return "gozero"; // Default style
    }

    /**
     * Load configuration from project
     * @param project The project
     * @param apiFile The API file (can be null)
     * @return Loaded config or null
     */
    @Nullable
    private static JzeroConfig loadConfig(@NotNull Project project, @Nullable PsiFile apiFile) {
        // First try based on API file path
        if (apiFile != null) {
            VirtualFile apiVirtualFile = apiFile.getVirtualFile();
            if (apiVirtualFile != null) {
                String apiPath = apiVirtualFile.getPath();
                // Extract path before /desc/ directory
                int descIndex = apiPath.indexOf("/desc/");
                if (descIndex > 0) {
                    String basePath = apiPath.substring(0, descIndex);
                    String configPath = basePath + "/" + CONFIG_FILENAME;

                    // Try to create VirtualFile for the config path
                    try {
                        com.intellij.openapi.vfs.LocalFileSystem fs = com.intellij.openapi.vfs.LocalFileSystem.getInstance();
                        VirtualFile configFile = fs.findFileByPath(configPath);
                        if (configFile != null && configFile.exists()) {
                            return readConfigFromFile(configFile);
                        }
                    } catch (Exception e) {
                        // Fallback to project-wide search if VirtualFile creation fails
                    }
                }
            }
        }

        // Fallback: try project root directory
        Collection<VirtualFile> yamlFiles = FilenameIndex.getAllFilesByExt(project, "yaml", GlobalSearchScope.projectScope(project));

        for (VirtualFile file : yamlFiles) {
            if (CONFIG_FILENAME.equals(file.getName())) {
                return readConfigFromFile(file);
            }
        }

        return null;
    }

    @Nullable
    private static JzeroConfig readConfigFromFile(@NotNull VirtualFile configFile) {
        try (InputStream is = configFile.getInputStream()) {
            Yaml yaml = new Yaml();

            // 读取 YAML 文件为 Map
            Map<String, Object> configMap = yaml.load(is);

            if (configMap == null) {
                return null;
            }

            // Debug: 打印解析到的内容
            System.out.println("DEBUG: ConfigMap keys: " + configMap.keySet());

            // 获取 gen 配置
            Object genConfig = configMap.get("gen");
            System.out.println("DEBUG: genConfig type: " + (genConfig != null ? genConfig.getClass().getSimpleName() : "null"));
            System.out.println("DEBUG: genConfig value: " + genConfig);

            if (!(genConfig instanceof Map)) {
                return null;
            }

            // 安全的类型转换
            Map<String, Object> genMap;
            try {
                genMap = (Map<String, Object>) genConfig;
            } catch (ClassCastException e) {
                System.out.println("DEBUG: Failed to cast genConfig to Map");
                return null;
            }

            // 获取 style 配置
            Object style = genMap.get("style");
            System.out.println("DEBUG: genMap keys: " + genMap.keySet());
            System.out.println("DEBUG: style type: " + (style != null ? style.getClass().getSimpleName() : "null"));
            System.out.println("DEBUG: style value: " + style);

            if (!(style instanceof String)) {
                return null;
            }

            // 创建配置对象
            JzeroConfig config = new JzeroConfig();
            GenConfig genConfigObj = new GenConfig();
            genConfigObj.setStyle((String) style);
            config.setGen(genConfigObj);

            return config;

        } catch (IOException e) {
            // 读取文件失败
            return null;
        } catch (Exception e) {
            // YAML 解析失败
            return null;
        }
    }

    /**
     * Format file name based on jzero format specification
     * Examples:
     * - "gozero", "access_grant" -> "accessgrant"
     * - "go_zero", "access_grant" -> "access_grant"
     * - "go#zero", "access_grant" -> "access#grant"
     * - "goZero", "AccessGrant" -> "accessGrant"
     */
    @NotNull
    public static String formatFileName(@NotNull String formatStyle, @NotNull String content) {
        if ( content.isEmpty()) {
            return content;
        }

        // Handle other common formats
        switch (formatStyle) {
            case "goZero":
                return toCamelCase(content);
            case "go_zero":
                return toSnakeCase(content);
            case "go-zero":
                return toKebabCase(content);
            default:
                // Default to lowercase for unknown formats
                return content.toLowerCase();
        }
    }

    /**
     * Convert string to camelCase
     */
    @NotNull
    private static String toCamelCase(@NotNull String input) {
        if (input.isEmpty()) {
            return input;
        }

        String[] parts = input.split("[_\\-\\s]+");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].toLowerCase();
            if (i == 0) {
                result.append(part);
            } else {
                if (!part.isEmpty()) {
                    result.append(Character.toUpperCase(part.charAt(0)));
                    if (part.length() > 1) {
                        result.append(part.substring(1));
                    }
                }
            }
        }

        return result.toString();
    }

    /**
     * Convert string to snake_case
     */
    @NotNull
    private static String toSnakeCase(@NotNull String input) {
        if (input.isEmpty()) {
            return input;
        }

        // Insert underscore before uppercase letters and convert to lowercase
        String result = input.replaceAll("([a-z])([A-Z])", "$1_$2");
        // Replace hyphens and spaces with underscores
        result = result.replaceAll("[\\-\\s]+", "_");
        // Convert multiple underscores to single underscore
        result = result.replaceAll("_+", "_");
        // Convert to lowercase
        return result.toLowerCase();
    }

    /**
     * Convert string to kebab-case
     */
    @NotNull
    private static String toKebabCase(@NotNull String input) {
        return toSnakeCase(input).replaceAll("_", "-");
    }

    /**
     * Remove common separators (underscores, hyphens) from string
     */
    @NotNull
    private static String removeSeparators(@NotNull String input) {
        return input.replaceAll("[_\\-\\s]+", "");
    }

    /**
     * Check if a style string indicates uppercase formatting
     */
    private static boolean isUpperCaseStyle(@NotNull String style) {
        return style.equals(style.toUpperCase());
    }
}