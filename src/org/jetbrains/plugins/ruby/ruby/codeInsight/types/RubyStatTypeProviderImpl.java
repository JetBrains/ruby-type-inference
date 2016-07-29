package org.jetbrains.plugins.ruby.ruby.codeInsight.types;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.codeInsight.resolve.ResolveUtil;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.Symbol;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.SymbolUtil;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement;
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RExpression;
import org.jetbrains.plugins.ruby.ruby.lang.psi.impl.references.RDotReferenceImpl;
import org.jetbrains.plugins.ruby.ruby.lang.psi.impl.variables.RIdentifierImpl;
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.RCall;
import org.jetbrains.plugins.ruby.ruby.lang.psi.references.RDotReference;
import org.jetbrains.plugins.ruby.ruby.lang.psi.references.RReference;
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.RIdentifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RubyStatTypeProviderImpl implements RubyStatTypeProvider {
    private class CallIdentifier {
        String methodFqn;
        List<String> argFqns;

        CallIdentifier(String methodFqn, List<String> argFqns) {
            this.methodFqn = methodFqn;
            this.argFqns = argFqns;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CallIdentifier that = (CallIdentifier) o;

            if (methodFqn != null ? !methodFqn.equals(that.methodFqn) : that.methodFqn != null) return false;
            return argFqns != null ? argFqns.equals(that.argFqns) : that.argFqns == null;

        }

        @Override
        public int hashCode() {
            int result = methodFqn != null ? methodFqn.hashCode() : 0;
            result = 31 * result + (argFqns != null ? argFqns.hashCode() : 0);
            return result;
        }
    }

    static private Map<CallIdentifier, RType> callsReturnTypes = new HashMap<CallIdentifier, RType>();
//    {{
//        put(
//            new CallIdentifier(
//                "foo",
//                new ArrayList<String>() {{
//                    add("Fixnum");
//                    add("Float");
//                    add("String");
//                }}
//            ),
//            RTypeFactory.createTypeByFQN(getProject(), "Float")
//        );
//    }};


    @Override
    @Nullable
    public RType createTypeByCallAndArgs(@NotNull RExpression call, @NotNull List<RPsiElement> callArgs) {
        final List<String> argTypesFqns = callArgs.stream()
//            .filter(arg -> arg instanceof RExpression)
                .map(arg -> ((RExpression)arg).getType().getPresentableName())
                .collect(Collectors.toList());

        CallIdentifier callId;
        final PsiElement element2resolve = call instanceof RCall ? ((RCall)call).getPsiCommand() : call;
        Symbol symbol = ResolveUtil.resolveToSymbolWithCaching(element2resolve.getReference(), false);
        if (symbol != null) {
            final String methodFqn = SymbolUtil.getSymbolFullQualifiedName(symbol);
            callId = new CallIdentifier(methodFqn, argTypesFqns);
        } else if (element2resolve instanceof RReference) {
            final RPsiElement receiver = ((RReference) element2resolve).getReceiver();
            symbol = ResolveUtil.resolveToSymbolWithCaching(receiver.getReferenceEx(false));
            final String receiverFqn = symbol != null ? SymbolUtil.getSymbolFullQualifiedName(symbol) : receiver.getName();
            final String methodName = ((RDotReferenceImpl) element2resolve).getName();
            final String methodFqn = receiverFqn + "." + methodName;
            callId = new CallIdentifier(methodFqn, argTypesFqns);
        } else {
            final String methodFqn = "main." + ((RIdentifier) element2resolve).getName();
            callId = new CallIdentifier(methodFqn, argTypesFqns);
        }

        callsReturnTypes.put(callId, callArgs.isEmpty() ? null : RTypeFactory.createTypeByFQN(call.getProject(), "Float"));

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