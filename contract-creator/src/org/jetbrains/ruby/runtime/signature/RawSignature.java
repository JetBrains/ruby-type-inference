package org.jetbrains.ruby.runtime.signature;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.ruby.codeInsight.types.signature.*;
import org.jetbrains.ruby.runtime.signature.server.ServerResponseBean;

import java.util.*;

public class RawSignature {
    @NotNull
    private final RMethodInfo myMethodInfo;
    @NotNull
    private final List<ParameterInfo> myArgsInfo;
    @NotNull
    private final List<Boolean> isGiven;
    @NotNull
    private List<String> myArgsTypes;

    private final String myCallMid;
    private int myCallArgc;
    @NotNull
    private final List<String> myKeyWords;
    private String myReturnTypeName;

    public RawSignature(ServerResponseBean bean) {

        this.myMethodInfo = new RMethodInfo(bean.method_name,
                bean.receiver_name,
                RVisibility.valueOf(bean.visibility),
                new GemInfo(bean.gem_name, bean.gem_version));

        this.myCallMid = bean.call_info_mid;

        if (!myCallMid.equals("nil")) {
            this.myCallArgc = bean.call_info_argc;
            String kwInfo = bean.call_info_kw_args;

            this.myKeyWords = new ArrayList<>();
            if (!kwInfo.equals("")) {
                this.myKeyWords.addAll(Arrays.asList(kwInfo.split("\\s*,\\s*")));
            }
        } else {
            this.myKeyWords = new ArrayList<>();
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

//     parameter information
//
//      def m(a1, a2, ..., aM,                    # mandatory
//             b1=(...), b2=(...), ..., bN=(...),  # optional
//             *c,                                 # rest
//             d1, d2, ..., dO,                    # post
//             e1:(...), e2:(...), ..., eK:(...),  # keyword
//             **f,                                # keyword_rest
//             &g)                                 # block

    private void updateArgExists(ParameterInfo argument, int i) {
        if (myCallArgc <= 0)
            return;

        if (myCallArgc > myKeyWords.size()) {
            myCallArgc--;
        } else {
            for (String kwName : myKeyWords) {
                //argument.addInfo(kwName);
                myCallArgc--;
            }
            myKeyWords.clear();
        }
        isGiven.set(i, Boolean.TRUE);
    }

    public void fetch() {

        boolean keyflag = false;

        for (int i = 0; i < myArgsTypes.size(); i++) {
            ParameterInfo argument = myArgsInfo.get(i);
            String type = myArgsTypes.get(i);

            if (argument.getModifier() == ParameterInfo.Type.BLOCK) {
                if (!type.equals("NillClass"))
                    isGiven.set(i, Boolean.TRUE);
            }

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

        for (int i = 0; i < myArgsInfo.size(); i++) {
            ParameterInfo argument = myArgsInfo.get(i);
            if (argument.getModifier() == ParameterInfo.Type.REQ) {
                updateArgExists(argument, i);
            }
        }

        if (myKeyWords.size() == 0) {

            for (int i = 0; i < myArgsInfo.size(); i++) {
                ParameterInfo argument = myArgsInfo.get(i);
                if (argument.getModifier() == ParameterInfo.Type.OPT) {
                    updateArgExists(argument, i);
                }
            }
        } else {
            if (!keyflag) {
                for (int i = 0; i < myArgsInfo.size(); i++) {
                    ParameterInfo argument = myArgsInfo.get(i);
                    if (argument.getModifier() == ParameterInfo.Type.OPT) {
                        updateArgExists(argument, i);
                    }
                }
            } else {
                for (int i = 0; i < myArgsInfo.size(); i++) {
                    ParameterInfo argument = myArgsInfo.get(i);
                    String argumentName = argument.getName();

                    if (argument.getModifier() == ParameterInfo.Type.KEY) {
                        if (myKeyWords.contains(argumentName)) {
                            isGiven.set(i, Boolean.TRUE);
                            myCallArgc--;
                            myKeyWords.remove(argumentName);
                        }

                    }
                    if (argument.getModifier() == ParameterInfo.Type.KEYREQ) {
                        if (myKeyWords.contains(argumentName)) {
                            isGiven.set(i, Boolean.TRUE);
                            myCallArgc--;
                            myKeyWords.remove(argumentName);
                        }
                    }
                    if (argument.getModifier() == ParameterInfo.Type.KEYREST) {
                        if (!myKeyWords.isEmpty()) {
                            isGiven.set(i, Boolean.TRUE);
                            myCallArgc -= myKeyWords.size();
                        }
                        myKeyWords.clear();
                    }
                }
            }
        }

        for (int i = 0; i < myArgsInfo.size(); i++) {

            ParameterInfo argument = myArgsInfo.get(i);
            if (argument.getModifier() == ParameterInfo.Type.REST) {
                if (this.getCallArgc() > 0) {
                    isGiven.set(i, Boolean.TRUE);
                    myCallArgc = 0;
                    //for (String kwName : myKeyWords) {
                    //    argument.addInfo(kwName);
                    //}
                    myKeyWords.clear();
                }
            }
        }
    }

    public RSignature getRSignature() {

        return new RSignature(myMethodInfo, myArgsInfo, myArgsTypes, myReturnTypeName);
    }

    @NotNull
    public List<Boolean> getIsGiven() {
        return isGiven;
    }

    public void changeArgumentType(int index, String newType) {
        myArgsTypes.set(index, newType);
    }

    private int getCallArgc() {
        return myCallArgc;
    }
}
