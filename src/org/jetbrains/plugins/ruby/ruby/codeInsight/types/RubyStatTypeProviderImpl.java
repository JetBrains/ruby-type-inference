package org.jetbrains.plugins.ruby.ruby.codeInsight.types;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.codeInsight.resolve.ResolveUtil;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.RMethodSymbolImpl;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.Symbol;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.v2.TopLevelSymbol;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.impl.RSymbolTypeImpl;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement;
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RExpression;
import org.jetbrains.plugins.ruby.ruby.lang.psi.impl.references.RDotReferenceImpl;
import org.jetbrains.plugins.ruby.ruby.lang.psi.impl.references.RReferenceBase;
import org.jetbrains.plugins.ruby.ruby.lang.psi.impl.variables.RIdentifierImpl;
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.RCall;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RubyStatTypeProviderImpl implements RubyStatTypeProvider {
    private class CallIdentifier {
        String methodName;
        String receiverName;
        List<String> argTypeNames;

        CallIdentifier(Symbol receiver, Symbol method, List<RType> argTypes) {
            this.methodName = method != null ? method.getName() : null;
            this.receiverName = receiver != null ? receiver.getName() : null;
            this.argTypeNames = argTypes.stream()
                    .map(RType::getPresentableName)
                    .collect(Collectors.toList());
        }

        CallIdentifier(String receiverName, String methodName, List<RType> argTypes) {
            this.methodName = methodName;
            this.receiverName = receiverName;
            this.argTypeNames = argTypes.stream()
                    .map(RType::getPresentableName)
                    .collect(Collectors.toList());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CallIdentifier that = (CallIdentifier) o;

            if (methodName != null ? !methodName.equals(that.methodName) : that.methodName != null) return false;
            if (receiverName != null ? !receiverName.equals(that.receiverName) : that.receiverName != null)
                return false;
            return argTypeNames != null ? argTypeNames.equals(that.argTypeNames) : that.argTypeNames == null;

        }

        @Override
        public int hashCode() {
            int result = methodName != null ? methodName.hashCode() : 0;
            result = 31 * result + (receiverName != null ? receiverName.hashCode() : 0);
            result = 31 * result + (argTypeNames != null ? argTypeNames.hashCode() : 0);
            return result;
        }
    }

    static private Map<CallIdentifier, RType> callsReturnTypes = new HashMap<CallIdentifier, RType>();
//    {{
//        put(
//            new CallIdentifier(
//                new TopLevelSymbol(),
//                new RMethodSymbolImpl(),
//                new ArrayList<RType>() {{
//                    add(new RSymbolTypeImpl());
//                    add(new RSymbolTypeImpl());
//                    add(new RSymbolTypeImpl());
//                }}
//            ),
//            new RSymbolTypeImpl()
//        );
//    }};


    @Override
    @Nullable
    public RType createTypeByCallAndArgs(@NotNull RExpression call, @NotNull List<RPsiElement> callArgs) {
        final List<RType> argTypes = callArgs.stream()
//            .filter(arg -> arg instanceof RExpression)
                .map(arg -> ((RExpression)arg).getType())
                .collect(Collectors.toList());

        CallIdentifier callId;
        final PsiElement element2resolve = call instanceof RCall ? ((RCall)call).getPsiCommand() : call;
        final Symbol method = ResolveUtil.resolveToSymbolWithCaching(element2resolve.getReference(), false);
        if (method != null) {
            final Symbol receiver = method.getParentSymbol();
            callId = new CallIdentifier(receiver, method, argTypes);
        } else if (element2resolve instanceof RDotReferenceImpl) {
            final String methodName = ((RDotReferenceImpl) element2resolve).getName();
            final String receiverName = ((RDotReferenceImpl) element2resolve).getReceiver().getName();
            callId = new CallIdentifier(receiverName, methodName, argTypes);
        } else {
            final String methodName = ((RIdentifierImpl) element2resolve).getName();
            callId = new CallIdentifier(null, methodName, argTypes);
        }

        callsReturnTypes.put(callId, argTypes.isEmpty() ? null : argTypes.get(0));

//        try {
//            File file = new File("temp");
//            FileOutputStream fos = new FileOutputStream(file);
//            ObjectOutputStream oos = new ObjectOutputStream(fos);
//            oos.writeObject(callsReturnTypes);
//            oos.close();
//        } catch (IOException e) {
//
//        }

//        HashMap<CallIdentifier, RType> callsReturnTypesLoaded = new HashMap<>();
//        try {
//            File file = new File("temp");
//            FileInputStream f = new FileInputStream(file);
//            ObjectInputStream s = new ObjectInputStream(f);
//            callsReturnTypesLoaded = (HashMap<CallIdentifier, RType>)s.readObject();
//            s.close();
//        } catch (ClassNotFoundException | IOException e) {
//
//        }

        return callsReturnTypes.containsKey(callId) ? callsReturnTypes.get(callId) : null;
    }
}