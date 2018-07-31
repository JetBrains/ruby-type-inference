package org.jetbrains.ruby.runtime.signature.server.serialisation;

import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ruby.codeInsight.types.signature.*;

import java.util.*;

public class RTupleBuilder {
    private static final Gson GSON = new Gson();

    @NotNull
    private final MethodInfo myMethodInfo;
    @NotNull
    private final List<ParameterInfo> myArgsInfo;
    @NotNull
    private final List<String> myArgsTypes;

    private final String myReturnTypeName;

    private RTupleBuilder(ServerResponseBean bean) {

        myMethodInfo = MethodInfoKt.MethodInfo(
                ClassInfoKt.ClassInfo(
                        GemInfoKt.GemInfoFromFilePathOrNull(bean.path),
                        beautifyClassName(bean.receiver_name)),
                bean.method_name,
                RVisibility.valueOf(bean.visibility),
                new Location(bean.path, bean.lineno));


        final int argc;
        if (!bean.call_info_argc.equals(""))
            argc = Integer.parseInt(bean.call_info_argc);
        else
            argc = 0;

        myReturnTypeName = bean.return_type_name;

        myArgsTypes = new ArrayList<>();
        //this.myArgsTypes.addAll(Arrays.asList(argsTypeName.split("\\s*;\\s*")));


        String argsInfo = bean.args_info;
        myArgsInfo = new ArrayList<>();
        if (!argsInfo.equals("")) {
            for (String argument : Arrays.asList(argsInfo.split("\\s*;\\s*"))) {
                List<String> parts = Arrays.asList(argument.split("\\s*,\\s*"));

                String name = null;

                if (parts.size() > 2 && !parts.get(2).equals("nil"))
                    name = parts.get(2);

                if (name == null) {
                    // TODO[viuginick] investigate nullability
//                    throw new RuntimeException("parse fail: <" + argsInfo + ">");
                    name = "FUCKYOU";
                }

                myArgsInfo.add(new ParameterInfo(name, ParameterInfo.Type.valueOf(parts.get(0))));
                myArgsTypes.add(parts.get(1));
            }
        }

        if (argc != -1) {
            Collection<String> kwArgs = Arrays.asList(bean.call_info_kw_args.split("\\s*,\\s*"));
            boolean[] flags = calcPresentArguments(myArgsInfo, argc, kwArgs);

            for (int i = 0; i < flags.length; i++) {
                if (!flags[i]) {
                    myArgsTypes.set(i, "-");
                }
            }
        }
    }

    @NotNull
    private String beautifyClassName(@NotNull String bean) {
        if (bean.length() > 90) {
            return bean.substring(0, 90) + "...";
        }
        return bean;
    }

    @Nullable
    public static RTuple fromJson(@NotNull String json) {
        final ServerResponseBean result = GSON.fromJson(json, ServerResponseBean.class);
        return result != null ? new RTupleBuilder(result).build() : null;
    }

    public static boolean[] calcPresentArguments(List<ParameterInfo> info, int argc, Collection<String> kwArgsImmutable) {
        final boolean[] isPresent = new boolean[info.size()];
        final Set<String> kwArgs = new HashSet<>(kwArgsImmutable);

        argc = updateFlags(info, ParameterInfo.Type.REQ, isPresent, argc, kwArgs);
        if (argc <= 0) {
            kwArgs.clear();
        }

        if (kwArgs.isEmpty() || !isKeyArgsPresent(info)) {
            argc = updateFlags(info, ParameterInfo.Type.OPT, isPresent, argc, kwArgs);
            if (argc <= 0) {
                kwArgs.clear();
            }
        } else {

            for (int i = 0; i < info.size(); i++) {
                ParameterInfo parameterInfo = info.get(i);
                String argumentName = parameterInfo.getName();

                final ParameterInfo.Type modifier = parameterInfo.getModifier();
                if (modifier == ParameterInfo.Type.KEY) {
                    if (kwArgs.contains(argumentName)) {
                        isPresent[i] = true;
                        argc--;
                        kwArgs.remove(argumentName);
                    }

                }
                if (modifier == ParameterInfo.Type.KEYREQ) {
                    if (kwArgs.contains(argumentName)) {
                        isPresent[i] = true;
                        argc--;
                        kwArgs.remove(argumentName);
                    }
                }
                if (modifier == ParameterInfo.Type.KEYREST) {
                    if (!kwArgs.isEmpty()) {
                        isPresent[i] = true;
                        argc -= kwArgs.size();
                    }
                    kwArgs.clear();
                }
            }

        }

        for (int i = 0; i < info.size(); i++) {
            ParameterInfo parameterInfo = info.get(i);
            if (parameterInfo.getModifier() == ParameterInfo.Type.REST && argc > 0) {
                isPresent[i] = true;
                break;
            }
        }

        return isPresent;
    }

    @NotNull
    private RTuple build() {
        return new RTuple(myMethodInfo, myArgsInfo, myArgsTypes, myReturnTypeName);
    }

    //     parameter information
    //
    //      def m(a1, a2, ..., aM,                    # mandatory
    //             b1=(...), b2=(...), ..., bN=(...),  # optional
    //             *c,                                 # rest
    //             d1, d2, ..., dO,                    # post
    //             e1:(...), e2:(...), ..., eK:(...),  # keyword
    //             **f,                                # keyword_rest
    //             &g)                                 # block


    private static int updateFlags(List<ParameterInfo> info, ParameterInfo.Type type, boolean[] isPresent, int argc, Set<String> kwArgs) {

        for (int i = 0; i < info.size(); i++) {
            ParameterInfo parameterInfo = info.get(i);
            if (parameterInfo.getModifier() == type) {
                if (argc <= 0)
                    continue;

                if (argc > kwArgs.size()) {
                    argc--;
                } else {
                    argc -= kwArgs.size();
                }
                isPresent[i] = true;
            }
        }

        return argc;
    }

    private static boolean isKeyArgsPresent(List<ParameterInfo> info) {
        boolean keyArgsPresent = false;

        for (ParameterInfo argument : info) {

            if (argument.getModifier() == ParameterInfo.Type.KEYREQ) {
                keyArgsPresent = true;
            }

            if (argument.getModifier() == ParameterInfo.Type.KEY) {
                keyArgsPresent = true;
            }

            if (argument.getModifier() == ParameterInfo.Type.KEYREST) {
                keyArgsPresent = true;
            }
        }
        return keyArgsPresent;
    }
}