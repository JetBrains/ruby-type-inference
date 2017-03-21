package org.jetbrains.ruby.codeInsight.types.signature;

import java.util.*;

public final class RSignatureFetcher {

    private int argc;
    private Set<String> kwArgs;

    public RSignatureFetcher(int argc, Set<String> kwArgs) {
        this.argc = argc;
        this.kwArgs = new HashSet<>(kwArgs);
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

    private List<Boolean> updateArgExists(int i, List<Boolean> flags) {

        if (argc <= 0)
            return flags;

        if (argc > kwArgs.size()) {
            argc--;
        } else {
            argc -= kwArgs.size();
            kwArgs.clear();
        }
        flags.set(i, Boolean.TRUE);

        return flags;
    }

    private List<Boolean> updateFlags(List<ParameterInfo> info, ParameterInfo.Type type, List<Boolean> flags) {

        for (int i = 0; i < info.size(); i++) {
            ParameterInfo parameterInfo = info.get(i);
            if (parameterInfo.getModifier() == type) {
                flags = updateArgExists(i, flags);
            }
        }

        return flags;
    }

    public List<Boolean> fetch(List<ParameterInfo> info) {

        List<Boolean> result = new ArrayList<>(Arrays.asList(new Boolean[info.size()]));
        Collections.fill(result, Boolean.FALSE);

        boolean keyflag = false;

        for (ParameterInfo argument : info) {

            if (argument.getModifier() == ParameterInfo.Type.KEYREQ) {
                keyflag = true;
            }

            if (argument.getModifier() == ParameterInfo.Type.KEY) {
                keyflag = true;
            }

            if (argument.getModifier() == ParameterInfo.Type.KEYREST) {
                keyflag = true;
            }
        }

        result = updateFlags(info, ParameterInfo.Type.REQ, result);

        if (kwArgs.size() == 0 || !keyflag) {
            result = updateFlags(info, ParameterInfo.Type.OPT, result);
        } else {

            for (int i = 0; i < info.size(); i++) {
                ParameterInfo parameterInfo = info.get(i);
                String argumentName = parameterInfo.getName();

                if (parameterInfo.getModifier() == ParameterInfo.Type.KEY) {
                    if (kwArgs.contains(argumentName)) {
                        result.set(i, Boolean.TRUE);
                        argc--;
                        kwArgs.remove(argumentName);
                    }

                }
                if (parameterInfo.getModifier() == ParameterInfo.Type.KEYREQ) {
                    if (kwArgs.contains(argumentName)) {
                        result.set(i, Boolean.TRUE);
                        argc--;
                        kwArgs.remove(argumentName);
                    }
                }
                if (parameterInfo.getModifier() == ParameterInfo.Type.KEYREST) {
                    if (!kwArgs.isEmpty()) {
                        result.set(i, Boolean.TRUE);
                        argc -= kwArgs.size();
                    }
                    kwArgs.clear();
                }
            }

        }

        for (int i = 0; i < info.size(); i++) {

            ParameterInfo parameterInfo = info.get(i);
            if (parameterInfo.getModifier() == ParameterInfo.Type.REST) {
                if (argc > 0) {
                    result.set(i, Boolean.TRUE);
                    argc = 0;
                    kwArgs.clear();
                }
            }
        }

        return result;
    }

}
