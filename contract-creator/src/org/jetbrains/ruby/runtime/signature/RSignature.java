package org.jetbrains.ruby.runtime.signature;

import org.jetbrains.ruby.codeInsight.types.signature.ParameterInfo;
import org.jetbrains.ruby.runtime.signature.server.ServerResponseBean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RSignature {
    private final String myMethodName;
    private final String myReceiverName;
    private final List<String> myArgsTypeName;
    private final String myGemName;
    private final String myGemVersion;

    private List<RMethodArgument> myArgsInfo;

    private final String myPath;
    private final String myCallMid;
    private final Integer myLineNumber;
    private final String myVisibility;
    private int myCallArgc;
    private final List<String> myKeyWords;
    private String myReturnTypeName;

    public RSignature (ServerResponseBean bean) {
        this.myMethodName = bean.method_name;
        this.myReceiverName = bean.receiver_name;
        this.myGemName = bean.gem_name;
        this.myGemVersion = bean.gem_version;
        this.myVisibility = bean.visibility;

        this.myCallMid = bean.call_info_mid;

        if (!myCallMid.equals("nil")) {
            this.myCallArgc = bean.call_info_argc;
            String kwInfo = bean.call_info_kw_args;

            this.myKeyWords = new ArrayList<>();
            if (!kwInfo.equals("")) {
                this.myKeyWords.addAll(Arrays.asList(kwInfo.split("\\s*,\\s*")));
            }
        } else {
            this.myKeyWords = null;
        }


        this.myReturnTypeName = bean.return_type_name;


        String argsTypeName = bean.args_type_name;
        this.myArgsTypeName = new ArrayList<>();
        if(!argsTypeName.equals("")) {
            this.myArgsTypeName.addAll(Arrays.asList(argsTypeName.split("\\s*;\\s*")));
        }

        String argsInfo = bean.args_info;
        this.myArgsInfo = new ArrayList<>();
        if(!argsInfo.equals("")) {
            for (String argument : Arrays.asList(argsInfo.split("\\s*;\\s*"))) {
                this.myArgsInfo.add(new RMethodArgument(argument));
            }
        }

        this.myPath = bean.path;
        this.myLineNumber = bean.lineno;
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

    private void updateArgExists(RMethodArgument argument)
    {
        if(myCallArgc <= 0)
            return;

        if(myCallArgc > myKeyWords.size()) {
            myCallArgc--;
        }
        else {
            for (String kwName : myKeyWords) {
                argument.addInfo(kwName);
                myCallArgc--;
            }
            myKeyWords.clear();
        }
        argument.setIsGiven(true);
    }

    public void fetch() {

        boolean keyflag = false;

        for (RMethodArgument argument : myArgsInfo) {
            if (argument.getArgModifier() == ParameterInfo.Type.BLOCK) {
                if(!argument.getType().equals("NillClass"))
                    argument.setIsGiven(true);
            }

            if (argument.getArgModifier() == ParameterInfo.Type.KEYREQ) {
                keyflag = true;
            }

            if (argument.getArgModifier() == ParameterInfo.Type.KEY) {
                keyflag = true;
            }

            if (argument.getArgModifier() == ParameterInfo.Type.KEYREST) {
                keyflag = true;
            }
        }

        for (RMethodArgument argument : myArgsInfo) {
            if (argument.getArgModifier() == ParameterInfo.Type.REQ) {
                updateArgExists(argument);
            }
        }

        if(myKeyWords.size() == 0){
            for (RMethodArgument argument : myArgsInfo) {
                if (argument.getArgModifier() == ParameterInfo.Type.OPT) {
                    updateArgExists(argument);
                }
            }
        }
        else
        {
            if(!keyflag)
            {
                for (RMethodArgument argument : myArgsInfo) {
                    if (argument.getArgModifier() == ParameterInfo.Type.OPT) {
                        updateArgExists(argument);
                    }
                }
            }
            else
            {
                for (RMethodArgument argument : myArgsInfo) {
                    String argumentName = argument.getName();

                    if (argument.getArgModifier() == ParameterInfo.Type.KEY) {
                        if (myKeyWords.contains(argumentName)) {
                            argument.setIsGiven(true);
                            myCallArgc--;
                            myKeyWords.remove(argumentName);
                        }

                    }
                    if (argument.getArgModifier() == ParameterInfo.Type.KEYREQ) {
                        if (myKeyWords.contains(argumentName)) {
                            argument.setIsGiven(true);
                            myCallArgc--;
                            myKeyWords.remove(argumentName);
                        }
                    }
                    if (argument.getArgModifier() == ParameterInfo.Type.KEYREST) {
                        if (!myKeyWords.isEmpty()) {
                            argument.setIsGiven(true);
                            myCallArgc -= myKeyWords.size();
                        }
                        myKeyWords.clear();
                    }
                }
            }
        }

        for (RMethodArgument argument : myArgsInfo) {

            if (argument.getArgModifier() == ParameterInfo.Type.REST) {
                if (this.getCallArgc() > 0) {
                    argument.setIsGiven(true);
                    myCallArgc = 0;
                    for (String kwName : myKeyWords) {
                        argument.addInfo(kwName);
                    }
                    myKeyWords.clear();
                }
            }
        }
    }

    public String getMethodName() {
        return myMethodName;
    }
    public int getCallArgc() {
        return myCallArgc;
    }
    public String getReceiverName() {
        return myReceiverName;
    }

    public List<RMethodArgument> getArgsInfo() {
        return myArgsInfo;
    }

    public String getGemName() {
        return myGemName;
    }

    public String getPath() { return myPath; }
    public Integer getLineNumber() { return myLineNumber; }

    public String getVisibility() {
        return myVisibility;
    }

    public String getGemVersion() {
        return myGemVersion;
    }

    public String getReturnTypeName() {
        return myReturnTypeName;
    }

    public String getCallMid() {
        return myCallMid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RSignature that = (RSignature) o;

        return myMethodName.equals(that.myMethodName) &&
                myReceiverName.equals(that.myReceiverName) &&
                myArgsTypeName.equals(that.myArgsTypeName) &&
                myVisibility.equals(that.myVisibility) &&
                myGemName.equals(that.myGemName) &&
                myReturnTypeName.equals(that.myReturnTypeName) &&
                myGemVersion.equals(that.myGemVersion);

    }

    @Override
    public int hashCode() {
        int result = myMethodName.hashCode();
        result = 31 * result + myReceiverName.hashCode();
        result = 31 * result + myArgsTypeName.hashCode();
        result = 31 * result + myGemName.hashCode();
        result = 31 * result + myVisibility.hashCode();
        result = 31 * result + myGemVersion.hashCode();
        result = 31 * result + myReturnTypeName.hashCode();
        return result;
    }
}
