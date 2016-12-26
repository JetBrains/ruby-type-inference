package signature;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class RestrictedRSignature {
    @NotNull
    private final String myMethodName;
    @NotNull
    private final String myReceiverName;
    @NotNull
    private final RestrictedVisibility myVisibility;
    @NotNull
    private final List<RestrictedParameterInfo> myArgsInfo;
    @NotNull
    private final List<String> myArgsTypeName;
    @NotNull
    private final String myGemName;
    @NotNull
    private final String myGemVersion;
    @NotNull
    private String myReturnTypeName;
    private boolean myIsLocal;

    public RestrictedRSignature(@NotNull final String methodName, @NotNull final String receiverName,
                                @NotNull final RestrictedVisibility visibility,
                                @NotNull final List<RestrictedParameterInfo> argsInfo,
                                @NotNull final List<String> argsTypeName,
                                @NotNull final String gemName, @NotNull final String gemVersion,
                                @NotNull final String returnTypeName, final boolean isLocal) {
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
    public RestrictedVisibility getVisibility() {
        return myVisibility;
    }

    @NotNull
    public List<RestrictedParameterInfo> getArgsInfo() {
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

    public boolean isLocal() {
        return myIsLocal;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final RestrictedRSignature signature = (RestrictedRSignature) o;

        if (!myMethodName.equals(signature.myMethodName)) return false;
        if (!myReceiverName.equals(signature.myReceiverName)) return false;
        if (!myArgsTypeName.equals(signature.myArgsTypeName)) return false;
        if (!myGemName.equals(signature.myGemName)) return false;
        if (!myGemVersion.equals(signature.myGemVersion)) return false;
        return myReturnTypeName.equals(signature.myReturnTypeName);
    }

    @Override
    public int hashCode() {
        int result = myMethodName.hashCode();
        result = 31 * result + myReceiverName.hashCode();
        result = 31 * result + myArgsTypeName.hashCode();
        result = 31 * result + myGemName.hashCode();
        result = 31 * result + myGemVersion.hashCode();
        result = 31 * result + myReturnTypeName.hashCode();
        return result;
    }
}
