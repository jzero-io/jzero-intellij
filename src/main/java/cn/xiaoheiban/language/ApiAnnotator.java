package cn.xiaoheiban.language;

import cn.xiaoheiban.antlr4.ApiLexer;
import cn.xiaoheiban.antlr4.ApiParser;
import cn.xiaoheiban.highlighting.ApiSyntaxHighlighter;
import cn.xiaoheiban.parser.ApiParserDefinition;
import cn.xiaoheiban.psi.ApiFile;
import cn.xiaoheiban.psi.IdentifierPSINode;
import cn.xiaoheiban.psi.nodes.*;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import org.antlr.jetbrains.adapter.psi.ScopeNode;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ApiAnnotator implements Annotator {

    private AnnotationHolder mHolder;
    private Map<IElementType, List<ASTNode>> allNode;

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        mHolder = holder;

        // For all elements that could be type references, use navigation logic
        PsiReference ref = element.getReference();
        if (ref != null) {
            PsiElement resolved = ref.resolve();
            // If navigation works (resolved != null), no error
            // If navigation fails, then show error
            if (resolved == null && element.getText() != null && !element.getText().isEmpty()) {
                holder.createErrorAnnotation(element, "can not resolve " + element.getText());
            }
            return;
        }

        if (!(element instanceof IPsiNode)) {
            return;
        }
        if (element instanceof ApiRootNode) {
            ApiRootNode root = (ApiRootNode) element;
            allNode = root.getAllNode();
            Map<IElementType, Set<ASTNode>> duplicateNode = ApiRootNode.getAllDuplicateNode(allNode);
            duplicateNode.forEach((et, nodes) -> {
                if (nodes.size() > 1) {
                    if (et.equals(ApiParserDefinition.rule(ApiParser.RULE_structNameId))) {
                        for (ASTNode node : nodes) {
                            mHolder.createErrorAnnotation(node, "duplicate struct " + node.getText());
                        }
                    } else if (et.equals(ApiParserDefinition.rule(ApiParser.RULE_handlerValue))) {
                        for (ASTNode node : nodes) {
                            mHolder.createErrorAnnotation(node, "duplicate handler " + node.getText());
                        }
                    } else {// route
                        for (ASTNode node : nodes) {
                            mHolder.createErrorAnnotation(node, "duplicate route " + node.getText());
                        }
                    }
                }
            });

        } else if (element instanceof StructNode) {
            StructNode node = (StructNode) element;
            Map<String, Set<PsiElement>> duplicateField = node.getDuplicateField();
            duplicateField.forEach((s, psiElements) -> {
                if (psiElements == null || s == null) return;
                psiElements.forEach(el -> {
                    if (el == null) return;
                    mHolder.createErrorAnnotation(el, "filed [" + s + "] redeclare in this struct");
                });
            });
        } else if (element instanceof ServiceNameNode) {
            mHolder.createInfoAnnotation(element, element.getText()).setTextAttributes(ApiSyntaxHighlighter.IDENTIFIER);
        } else if (element instanceof ReferenceIdNode) {//RULE_referenceId
            if (element.getText().contains(".")) {
                return;
            }

            // ReferenceIdNode should be handled by the general logic above
            // No need for special handling here
        } else if (element instanceof BodyNode) {//RULE_body
            if (element.getText().contains(".")) {
                return;
            }

            PsiElement lastChild = element.getLastChild();
            if (lastChild == null) {
                return;
            }

            if (lastChild instanceof LeafPsiElement) {
                LeafPsiElement leafPsiElement = (LeafPsiElement) lastChild;
                if (leafPsiElement.getElementType().equals(ApiParserDefinition.GOTYPE)) {
                    return;
                }
            }

            // BodyNode should be handled by the general logic above
            // No need for special handling here

            // Also handle the lastChild for BodyNode specifically
            PsiElement bodyLastChild = element.getLastChild();
            if (bodyLastChild != null) {
                PsiReference childRef = bodyLastChild.getReference();
                if (childRef != null && childRef.resolve() == null && bodyLastChild.getText() != null && !bodyLastChild.getText().isEmpty()) {
                    holder.createErrorAnnotation(element, "can not resolve " + bodyLastChild.getText());
                }
            }
        } else if (element instanceof AnonymousField) {
            // AnonymousField should be handled by the general logic above
            // But also check the nameNode specifically
            PsiElement nameNode = ((AnonymousField) element).getNameNode();
            if (nameNode != null) {
                PsiReference nameRef = nameNode.getReference();
                if (nameRef != null && nameRef.resolve() == null && nameNode.getText() != null && !nameNode.getText().isEmpty()) {
                    holder.createErrorAnnotation(element, "can not resolve " + nameNode.getText());
                }
            }
        }
    }


}
