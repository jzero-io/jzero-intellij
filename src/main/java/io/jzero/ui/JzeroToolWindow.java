package io.jzero.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;

import javax.swing.*;
import java.awt.*;

/**
 * Jzero Tool Window - API URL 输入框和弹窗
 */
public class JzeroToolWindow extends SimpleToolWindowPanel {
    private JTextField urlInputField;

    public JzeroToolWindow(Project project) {
        super(false, true);
        initUI();
    }

    private void initUI() {
        // 创建主面板
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 标题标签
        JLabel titleLabel = new JLabel("");
        titleLabel.setFont(new Font(titleLabel.getFont().getName(), Font.BOLD, 14));
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        // 设置内容
        setContent(mainPanel);
    }
}