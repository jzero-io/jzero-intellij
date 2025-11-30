package io.jzero.psi.nodes;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

public class TagNode extends IPsiNode {
    public TagNode(@NotNull ASTNode node) {
        super(node);
    }
}
