package org.jetbrains.ruby.runtime.signature;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.ruby.codeInsight.types.signature.*;
import org.jetbrains.ruby.runtime.signature.server.ServerResponseBean;

import java.util.*;


public class RawSignature {
    @NotNull
    private final MethodInfo myMethodInfo;
    @NotNull
    private final List<ParameterInfo> myArgsInfo;
    @NotNull
    private final List<Boolean> isGiven;
    @NotNull
    private List<String> myArgsTypes;

    private final String myCallMid;
    public int argc;
    @NotNull
    public final Set<String> kwArgs;
    private String myReturnTypeName;

    public RawSignature(ServerResponseBean bean) {

        this.myMethodInfo = MethodInfoKt.MethodInfo(
                ClassInfoKt.ClassInfo(GemInfoKt.GemInfo(bean.gem_name, bean.gem_version), bean.receiver_name),
                bean.method_name,
                RVisibility.valueOf(bean.visibility));

        this.myCallMid = bean.call_info_mid;


        this.kwArgs = new HashSet<>();
        if (!myCallMid.equals("nil")) {
            this.argc = bean.call_info_argc;
            String kwInfo = bean.call_info_kw_args;

            if (!kwInfo.equals("")) {
                this.kwArgs.addAll(Arrays.asList(kwInfo.split("\\s*,\\s*")));
            }
        }

        this.myReturnTypeName = bean.return_type_name;


        String argsTypeName = bean.args_type_name;
        this.myArgsTypes = new ArrayList<>();
        if (!argsTypeName.equals("")) {
            this.myArgsTypes.addAll(Arrays.asList(argsTypeName.split("\\s*;\\s*")));
        }

        String argsInfo = bean.args_info;
        this.myArgsInfo = new ArrayList<>();
        if (!argsInfo.equals("")) {
            for (String argument : Arrays.asList(argsInfo.split("\\s*;\\s*"))) {
                List<String> parts = Arrays.asList(argument.split("\\s*,\\s*"));
                this.myArgsInfo.add(new ParameterInfo(parts.get(1), ParameterInfo.Type.valueOf(parts.get(0).toUpperCase(Locale.US))));
            }
        }

        isGiven = new ArrayList<>(Arrays.asList(new Boolean[myArgsTypes.size()]));
        Collections.fill(isGiven, Boolean.FALSE);
    }

    public RSignature getRSignature() {

        return new RSignature(myMethodInfo, myArgsInfo, myArgsTypes, myReturnTypeName);
    }

    @NotNull
    public List<ParameterInfo> getArgsInfo() {
        return myArgsInfo;
    }

    public void changeArgumentType(int index, String newType) {
        myArgsTypes.set(index, newType);
    }
}
