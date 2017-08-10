package org.jetbrains.ruby.codeInsight.types.signature;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RTuple {

    @NotNull
    private final MethodInfo myMethodInfo;

    @NotNull
    private final List<ParameterInfo> myArgsInfo;
    @NotNull
    private final List<String> myArgsTypes;
    @NotNull
    private final String myReturnTypeName;

    public RTuple(@NotNull final MethodInfo methodInfo,
                  @NotNull final List<ParameterInfo> argsInfo,
                  @NotNull final List<String> argsTypeName,
                  @NotNull final String returnTypeName) {
        myMethodInfo = methodInfo;
        myArgsInfo = argsInfo;
        myArgsTypes = argsTypeName;
        myReturnTypeName = returnTypeName;
    }

    @NotNull
    public MethodInfo getMethodInfo() {
        return myMethodInfo;
    }

    @NotNull
    List<ParameterInfo> getArgsInfo() {
        return myArgsInfo;
    }

    @NotNull
    List<String> getArgsTypes() {
        return myArgsTypes;
    }

    @NotNull
    String getReturnTypeName() {
        return myReturnTypeName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RTuple that = (RTuple) o;

        return myMethodInfo.equals(that.myMethodInfo) &&
                myArgsInfo.equals(that.myArgsInfo) &&
                myArgsTypes.equals(that.myArgsTypes);

    }

    @Override
    public int hashCode() {
        int result = myMethodInfo.hashCode();
        result = 31 * result + myArgsInfo.hashCode();
        result = 31 * result + myArgsTypes.hashCode();
        return result;
    }
}
