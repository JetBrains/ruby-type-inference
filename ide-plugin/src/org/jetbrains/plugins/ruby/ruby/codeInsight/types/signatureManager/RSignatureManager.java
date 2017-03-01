package org.jetbrains.plugins.ruby.ruby.codeInsight.types.signatureManager;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.util.io.StringRef;
import com.intellij.util.text.VersionComparatorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.Type;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.*;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.v2.SymbolPsiProcessor;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.Context;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.RType;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.impl.REmptyType;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.impl.RSymbolTypeImpl;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.ArgumentInfo;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.Visibility;
import org.jetbrains.ruby.codeInsight.types.signature.ParameterInfo;
import org.jetbrains.ruby.codeInsight.types.signature.RSignature;
import org.jetbrains.ruby.codeInsight.types.signature.RVisibility;

import java.util.*;
import java.util.stream.Collectors;

public abstract class RSignatureManager {
    @NotNull
    private static final Map<String, Pair<RType, Set<RSignature>>> ourSyntheticTypes = new HashMap<>();

    @NotNull
    public abstract List<String> findReturnTypeNamesBySignature(@NotNull final Project project, @Nullable final Module module,
                                                                @NotNull final RSignature signature);

    public abstract void recordSignature(@NotNull final RSignature signature);

    public abstract void deleteSignature(@NotNull final RSignature signature);

    public abstract void deleteSimilarSignatures(@NotNull final RSignature signature);

    public abstract List<RSignature> getSimilarSignatures(@NotNull final RSignature signature);

    public abstract List<RSignature> getLocalSignatures();

    public abstract void compact(@NotNull final Project project);

    public abstract void clear();

    public abstract long getStatFileLastModified(@NotNull final String gemName, @NotNull final String gemVersion);

    public abstract void setStatFileLastModified(@NotNull final String gemName, @NotNull final String gemVersion,
                                                 final long lastModified);

    @NotNull
    public RType createTypeByFQNFromStat(@NotNull final Project project, @NotNull final String classFQN) {
        final Set<RSignature> classMethodSignatures = getReceiverMethodSignatures(classFQN);
        if (classMethodSignatures.isEmpty()) {
            return REmptyType.INSTANCE;
        }

        if (ourSyntheticTypes.containsKey(classFQN)) {
            final Pair<RType, Set<RSignature>> cached = ourSyntheticTypes.get(classFQN);
            if (cached.getSecond().equals(classMethodSignatures)) {
                return cached.getFirst();
            }
        }

        final Symbol classSymbol = createSyntheticClassSymbol(project, classFQN, classMethodSignatures);
        final RType syntheticType = new RSymbolTypeImpl(classSymbol, Context.INSTANCE);
        ourSyntheticTypes.put(classFQN, Pair.create(syntheticType, classMethodSignatures));
        return syntheticType;
    }

    @NotNull
    public abstract List<ParameterInfo> getMethodArgsInfo(@NotNull final String methodName, @NotNull final String receiverName);

    @NotNull
    public static String getClosestGemVersion(@NotNull final String gemVersion, @NotNull final List<String> gemVersions) {
        final NavigableSet<String> sortedSet = new TreeSet<>(VersionComparatorUtil.COMPARATOR);
        sortedSet.addAll(gemVersions);

        final String upperBound = sortedSet.ceiling(gemVersion);
        final String lowerBound = sortedSet.floor(gemVersion);
        if (upperBound == null) {
            return lowerBound;
        } else if (lowerBound == null) {
            return upperBound;
        } else if (upperBound.equals(lowerBound)) {
            return upperBound;
        } else if (firstStringCloser(gemVersion, upperBound, lowerBound)) {
            return upperBound;
        } else {
            return lowerBound;
        }
    }

    @NotNull
    protected abstract Set<RSignature> getReceiverMethodSignatures(@NotNull final String receiverName);

    @NotNull
    private static Symbol createSyntheticClassSymbol(@NotNull final Project project,
                                                     @NotNull final String classFQN,
                                                     @NotNull final Set<RSignature> classMethodSignatures) {
        final List<RMethodSymbol> methodSymbols = new ArrayList<>();

        final Symbol classSymbol = new SymbolImpl(project, classFQN, Type.CLASS, null) {
            @NotNull
            @Override
            public Children getChildren() {
                return new ChildrenImpl() {
                    @Override
                    public boolean processChildren(@NotNull final SymbolPsiProcessor processor,
                                                   @Nullable final PsiElement invocationPoint) {
                        for (final RMethodSymbol methodSymbol : methodSymbols) {
                            if (!processor.process(methodSymbol)) {
                                return false;
                            }
                        }

                        return true;
                    }
                };
            }
        };

        methodSymbols.addAll(classMethodSignatures.stream()
                .map(signature -> new RMethodSyntheticSymbol(project,
                                                             signature.getMethodName(),
                                                             Type.INSTANCE_METHOD,
                                                             classSymbol,
                                                             castRVisibilityToVisibility(signature.getVisibility()),
                                                             signature.getArgsInfo().stream()
                                                                     .map(RSignatureManager::castParameterInfoToArgumentInfo)
                                                                     .collect(Collectors.toList())))
                .collect(Collectors.toList()));

        return classSymbol;
    }

    @NotNull
    private static Visibility castRVisibilityToVisibility(@NotNull final RVisibility visibility) {
        return Visibility.values()[visibility.ordinal()];
    }

    @NotNull
    private static ArgumentInfo castParameterInfoToArgumentInfo(@NotNull final ParameterInfo param) {
        return new ArgumentInfo(StringRef.fromString(param.getName()), castParameterTypeToArgumentType(param.getType()));
    }

    @NotNull
    private static ArgumentInfo.Type castParameterTypeToArgumentType(@NotNull final ParameterInfo.Type type) {
        switch (type) {
            case REQ:
            case KEYREQ:
                return ArgumentInfo.Type.SIMPLE;
            case OPT:
                return ArgumentInfo.Type.PREDEFINED;
            case KEY:
                return ArgumentInfo.Type.NAMED;
            case REST:
                return ArgumentInfo.Type.ARRAY;
            case KEYREST:
                return ArgumentInfo.Type.HASH;
            case BLOCK:
                return ArgumentInfo.Type.BLOCK;
            default:
                throw new IllegalArgumentException();
        }
    }

    private static boolean firstStringCloser(@NotNull final String gemVersion,
                                             @NotNull final String firstVersion, @NotNull final String secondVersion) {
        final int lcpLengthFirst = longestCommonPrefixLength(gemVersion, firstVersion);
        final int lcpLengthSecond = longestCommonPrefixLength(gemVersion, secondVersion);
        return (lcpLengthFirst > lcpLengthSecond || lcpLengthFirst > 0 && lcpLengthFirst == lcpLengthSecond &&
                Math.abs(gemVersion.charAt(lcpLengthFirst) - firstVersion.charAt(lcpLengthFirst)) <
                        Math.abs(gemVersion.charAt(lcpLengthFirst) - secondVersion.charAt(lcpLengthSecond)));
    }

    private static int longestCommonPrefixLength(@NotNull final String str1, @NotNull final String str2) {
        final int minLength = Math.min(str1.length(), str2.length());
        for (int i = 0; i < minLength; i++) {
            if (str1.charAt(i) != str2.charAt(i)) {
                return i;
            }
        }

        return minLength;
    }
}
