package io.jzero.completion;

import io.jzero.editor.AutoInsertHandler;
import io.jzero.parser.ApiParserDefinition;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;

import static com.intellij.patterns.PlatformPatterns.psiElement;

public class ApiCompletionContributor extends CompletionContributor {

    public ApiCompletionContributor() {
        extend(CompletionType.BASIC, typeDeclaration(), new ApiCompletionProvider(Priority.KEYWORD_PRIORITY, new AutoInsertHandler("")));
    }

    private static ElementPattern<? extends PsiElement> typeDeclaration() {
        return psiElement(ApiParserDefinition.IDENTIFIER);
    }
}

