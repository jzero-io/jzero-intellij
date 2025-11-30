package io.jzero.psi.nodes;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import io.jzero.language.ApiFileType;
import org.jetbrains.annotations.NotNull;

public class ImportValueNode extends IPsiNode implements PsiNamedElement {
    public ImportValueNode(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public String getName() {
        // Extract the file path from the import statement
        ASTNode lastChild = getNode().getLastChildNode();
        if (lastChild != null) {
            String text = lastChild.getText();
            // Remove quotes and get just the filename
            return text.replaceAll("\"", "");
        }
        return "";
    }

    public PsiElement setName(@NotNull String name) {
        // For simplicity, return this (can be implemented later)
        return this;
    }

    public PsiElement getNameIdentifier() {
        ASTNode lastChild = getNode().getLastChildNode();
        return lastChild != null ? lastChild.getPsi() : this;
    }

    @Override
    public PsiReference getReference() {
        return new ImportReference(this);
    }

    private static class ImportReference implements PsiReference {
        private final ImportValueNode element;

        public ImportReference(ImportValueNode element) {
            this.element = element;
        }

        @Override
        public PsiElement getElement() {
            return element;
        }

        @Override
        public TextRange getRangeInElement() {
            ASTNode lastChild = element.getNode().getLastChildNode();
            if (lastChild != null) {
                int startOffset = lastChild.getStartOffsetInParent();
                return new TextRange(startOffset, startOffset + lastChild.getTextLength());
            }
            return new TextRange(0, element.getTextLength());
        }

        @Override
        public PsiElement resolve() {
            String importPath = element.getName();
            if (importPath.isEmpty()) {
                return null;
            }

            // Get the directory containing the current file
            PsiFile containingFile = element.getContainingFile();
            if (containingFile == null) {
                return null;
            }

            PsiDirectory directory = containingFile.getContainingDirectory();
            if (directory == null) {
                return null;
            }

            // Find the imported file
            VirtualFile importedFile = directory.getVirtualFile().findFileByRelativePath(importPath);
            if (importedFile == null) {
                return null;
            }

            // Get the PsiFile for the imported file
            Project project = element.getProject();
            PsiManager psiManager = PsiManager.getInstance(project);
            PsiFile importedPsiFile = psiManager.findFile(importedFile);

            // Check if it's an API file
            if (importedPsiFile != null && importedPsiFile.getFileType() instanceof ApiFileType) {
                return importedPsiFile;
            }

            return null;
        }

        @NotNull
        @Override
        public String getCanonicalText() {
            return element.getName();
        }

        @Override
        public PsiElement handleElementRename(@NotNull String newElementName) {
            return element;
        }

        @Override
        public PsiElement bindToElement(@NotNull PsiElement element) {
            return element;
        }

        @Override
        public boolean isReferenceTo(@NotNull PsiElement element) {
            return resolve() == element;
        }

        @NotNull
        @Override
        public Object[] getVariants() {
            return new Object[0];
        }

        @Override
        public boolean isSoft() {
            return false;
        }
    }
}
