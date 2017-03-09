package org.jetbrains.plugins.ruby.ruby.codeInsight.types;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.codeInsight.resolve.ResolveUtil;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.fqn.FQN;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.Symbol;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.SymbolUtil;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.impl.REmptyType;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPossibleCall;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement;
import org.jetbrains.plugins.ruby.ruby.lang.psi.assoc.RAssoc;
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RExpression;
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.RArgumentToBlock;
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.RCall;
import org.jetbrains.plugins.ruby.ruby.lang.psi.references.RReference;
import org.jetbrains.ruby.codeInsight.types.signature.RSignatureContract;
import org.jetbrains.ruby.codeInsight.types.signature.RSignatureContractNode;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.ContractTransition;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.ReferenceContractTransition;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.TypedContractTransition;
import org.jetbrains.ruby.runtime.signature.server.SignatureServer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class RubyStatTypeProviderImpl implements RubyStatTypeProvider {

    @Nullable
    @Override
    public RType createTypeByCallAndArgs(@NotNull final RExpression call, @NotNull final List<RPsiElement> callArgs) {

        final PsiElement callElement = call instanceof RCall ? ((RCall) call).getPsiCommand() : call;

        SignatureServer callStatServer = SignatureServer.getInstance();

        final Couple<String> names = getMethodAndReceiverNames(callElement);
        final String methodName = names.getFirst();

        if (methodName != null) {

            final Module module = ModuleUtilCore.findModuleForPsiElement(call);
            final String receiverName = StringUtil.notNullize(names.getSecond(), CoreTypes.Object);

            RSignatureContract contract = callStatServer.getContractByMethodName(methodName);

            List<RSignatureContractNode> currNodes = new ArrayList<>();
            currNodes.add(contract.getStartNode());

            if (contract != null) {
                for (RPsiElement argument : callArgs) {
                    final List<String> argTypeNames = getArgTypeNames(argument);

                    List<RSignatureContractNode> nodes = new ArrayList<>();

                    for (RSignatureContractNode currNode : currNodes) {
                        for (String typeName : argTypeNames) {


                            if (currNode.getReferenceLinksFlag()) {
                                ContractTransition transition = currNode.getTransitionKeys().iterator().next();

                                int referenceIndex = ((ReferenceContractTransition) transition).link;

                                if (typeName.equals(argTypeNames.get(referenceIndex))) {
                                    nodes.add(currNode.goByTypeSymbol(transition));
                                }

                            } else {
                                TypedContractTransition transition = new TypedContractTransition(typeName);

                                if (currNode.containsKey(transition))
                                    nodes.add(currNode.goByTypeSymbol(transition));
                            }

                        }
                    }
                    currNodes = nodes;
                }
            }

            List<RType> returnTypes = new ArrayList<>();

            for (RSignatureContractNode currNode : currNodes) {
                for (ContractTransition transition : currNode.getTransitionKeys()) {
                    if (transition instanceof TypedContractTransition)
                        returnTypes.add(RTypeFactory.createTypeByFQN(call.getProject(), ((TypedContractTransition) transition).type));
                    else {
                        int referenceIndex = ((ReferenceContractTransition) transition).link;

                        final List<String> argTypeNames = getArgTypeNames(callArgs.get(referenceIndex));

                        for (String argTypeName : argTypeNames) {
                            returnTypes.add(RTypeFactory.createTypeByFQN(call.getProject(), argTypeName));
                        }
                    }
                }
            }

            if (returnTypes.size() == 1) {
                return returnTypes.get(0);
            } else if (returnTypes.size() <= RType.TYPE_TREE_HEIGHT_LIMIT) {
                return returnTypes.stream().reduce(REmptyType.INSTANCE, RTypeUtil::union);
            }

        }

        return null;
    }


    @NotNull
    private static Couple<String> getMethodAndReceiverNames(@NotNull final PsiElement methodElement) {
        String methodName = null;
        String receiverFQN = null;

        final Symbol methodSymbol = ResolveUtil.resolveToSymbolWithCaching(methodElement.getReference(), false);
        if (methodSymbol != null) {
            final FQN methodFQN = methodSymbol.getFQNWithNesting();
            methodName = methodFQN.getShortName();
            receiverFQN = methodFQN.getCallerFQN().getFullPath();
            return new Couple<>(methodName, receiverFQN);
        } else if (methodElement instanceof RPossibleCall) {
            methodName = ((RPossibleCall) methodElement).getName();
            if (methodElement instanceof RReference) {
                final RPsiElement receiver = ((RReference) methodElement).getReceiver();
                if (receiver instanceof RExpression) {
                    receiverFQN = ((RExpression) receiver).getType().getName();
                }
            } else {
                final Symbol receiverSymbol = SymbolUtil.getScopeContext(methodElement);
                if (receiverSymbol != null && SymbolUtil.isClassOrModuleSymbol(receiverSymbol.getType())) {
                    receiverFQN = receiverSymbol.getFQNWithNesting().getFullPath();
                }
            }
        }

        return new Couple<>(methodName, receiverFQN);
    }


    @NotNull
    private static List<String> getArgTypeNames(@NotNull RPsiElement arg) {
        if (arg instanceof RAssoc) {
            arg = ((RAssoc) arg).getValue();
        }

        final List<String> argTypeNames = new ArrayList<>();
        if (arg instanceof RExpression) {
            RType type = ((RExpression) arg).getType();
            if (type instanceof RUnionType) {
                final Deque<RType> types = new ArrayDeque<>();
                types.add(type);
                while (!types.isEmpty()) {
                    type = types.pop();
                    if (type instanceof RUnionType) {
                        types.add(((RUnionType) type).getType1());
                        types.add(((RUnionType) type).getType2());
                    } else {
                        argTypeNames.add(StringUtil.notNullize(type.getName(), CoreTypes.Object));
                    }
                }
            } else {
                argTypeNames.add(StringUtil.notNullize(type.getName(), CoreTypes.Object));
            }
        } else if (arg instanceof RArgumentToBlock) {
            argTypeNames.add(CoreTypes.Proc);
        }

        return argTypeNames;
    }
}
