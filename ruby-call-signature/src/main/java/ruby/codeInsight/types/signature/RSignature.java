package ruby.codeInsight.types.signature;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RSignature {
    @NotNull
    private final String myMethodName;
    @NotNull
    private final String myReceiverName;
    @NotNull
    private final RVisibility myVisibility;
    @NotNull
    private final List<ParameterInfo> myArgsInfo;
    @NotNull
    private final List<String> myArgsTypeName;
    @NotNull
    private final String myGemName;
    @NotNull
    private final String myGemVersion;
    @NotNull
    private String myReturnTypeName;
    private final boolean myIsLocal;

    public RSignature(@NotNull final String methodName,
                      @NotNull final String receiverName,
                      @NotNull final RVisibility visibility,
                      @NotNull final List<ParameterInfo> argsInfo,
                      @NotNull final List<String> argsTypeName,
                      @NotNull final String gemName,
                      @NotNull final String gemVersion,
                      @NotNull final String returnTypeName,
                      final boolean isLocal) {
        myMethodName = methodName;
        myReceiverName = receiverName;
        myVisibility = visibility;
        myArgsInfo = argsInfo;
        myArgsTypeName = argsTypeName;
        myGemName = gemName;
        myGemVersion = gemVersion;
        myReturnTypeName = returnTypeName;
        myIsLocal = isLocal;
    }

    @NotNull
    public String getMethodName() {
        return myMethodName;
    }

    @NotNull
    public String getReceiverName() {
        return myReceiverName;
    }

    @NotNull
    public RVisibility getVisibility() {
        return myVisibility;
    }

    @NotNull
    public List<ParameterInfo> getArgsInfo() {
        return myArgsInfo;
    }

    @NotNull
    public List<String> getArgsTypeName() {
        return myArgsTypeName;
    }

    @NotNull
    public String getGemName() {
        return myGemName;
    }

    @NotNull
    public String getGemVersion() {
        return myGemVersion;
    }

    @NotNull
    public String getReturnTypeName() {
        return myReturnTypeName;
    }

    public void setReturnTypeName(@NotNull final String returnTypeName) {
        this.myReturnTypeName = returnTypeName;
    }

    public boolean isLocal() {
        return myIsLocal;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RSignature that = (RSignature) o;

        return myMethodName.equals(that.myMethodName) &&
               myReceiverName.equals(that.myReceiverName) &&
               myVisibility.equals(that.myVisibility) &&
               myArgsInfo.equals(that.myArgsInfo) &&
               myArgsTypeName.equals(that.myArgsTypeName) &&
               myGemName.equals(that.myGemName) &&
               myGemVersion.equals(that.myGemVersion);

    }

    @Override
    public int hashCode() {
        int result = myMethodName.hashCode();
        result = 31 * result + myReceiverName.hashCode();
        result = 31 * result + myVisibility.hashCode();
        result = 31 * result + myArgsInfo.hashCode();
        result = 31 * result + myArgsTypeName.hashCode();
        result = 31 * result + myGemName.hashCode();
        result = 31 * result + myGemVersion.hashCode();
        return result;
    }
}
