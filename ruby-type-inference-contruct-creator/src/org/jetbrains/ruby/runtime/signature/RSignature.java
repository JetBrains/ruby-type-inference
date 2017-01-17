package org.jetbrains.ruby.runtime.signature;

import org.jetbrains.ruby.runtime.signature.server.ServerResponseBean;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class RSignature {
    private final String myMethodName;
    private final String myReceiverName;
    private final List<String> myArgsTypeName;
    private final String myGemName;
    private final String myGemVersion;

    private List<RMethodArgument> myArgsInfo;

    private final String myPath;
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
        this.myReturnTypeName = bean.return_type_name;
        this.myCallArgc = bean.call_info_argc;

        String argsTypeName = bean.args_type_name;
        this.myArgsTypeName = new LinkedList<>();
        if(!argsTypeName.equals("")) {
            for (String argumentName : Arrays.asList(argsTypeName.split("\\s*;\\s*"))) {
                this.myArgsTypeName.add(argumentName);
            }
        }

        String argsInfo = bean.args_info;
        this.myArgsInfo = new LinkedList<>();
        if(!argsInfo.equals("")) {
            for (String argument : Arrays.asList(argsInfo.split("\\s*;\\s*"))) {
                this.myArgsInfo.add(new RMethodArgument(argument));
            }
        }

        String kwInfo = bean.call_info_kw_args;
        this.myKeyWords = new LinkedList<>();
        if(!kwInfo.equals("")){
            for (String kwArg : Arrays.asList(kwInfo.split("\\s*,\\s*"))) {
                this.myKeyWords.add(kwArg);
            }
        }
        this.myPath = bean.path;
        this.myLineNumber = bean.lineno;
    }
    public RSignature(final String methodName, final String receiverName, final List<String> argsTypeName,
                      final String gemName, final String gemVersion, final String returnTypeName, final String visibility, final List<RMethodArgument> argsInfo, int argc, final List<String> keyWords, final String path, final Integer lineno) {
        this.myMethodName = methodName;
        this.myReceiverName = receiverName;
        this.myArgsTypeName = argsTypeName;
        this.myGemName = gemName;
        this.myGemVersion = gemVersion;
        this.myReturnTypeName = returnTypeName;
        this.myVisibility = visibility;
        this.myArgsInfo = argsInfo;
        this.myCallArgc = argc;
        this.myKeyWords = keyWords;
        this.myPath = path;
        this.myLineNumber = lineno;
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

    private void updateArgExists(int argIndex)
    {
        RMethodArgument argument = myArgsInfo.get(argIndex);
        String argumentName = argument.getName();

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
        //int argc = this.getCallArgc();

        int restPosition = -1;
        int kwPosition = -1;

        boolean keyreqflag = false;
        boolean keyflag = false;
        boolean keyrest = false;

        for(int i = 0; i < myArgsInfo.size(); i++)
        {
            RMethodArgument argument = myArgsInfo.get(i);
            String argumentName = argument.getName();

            if(argument.getArgModifier() == RMethodArgument.ArgModifier.block)
            {
                if(!argument.getType().equals("NillClass"))
                    argument.setIsGiven(true);
            }

            if(argument.getArgModifier() == RMethodArgument.ArgModifier.keyreq)
            {
                keyreqflag = true;
                keyflag = true;
            }

            if(argument.getArgModifier() == RMethodArgument.ArgModifier.key)
            {
                keyreqflag = true;
                keyflag = true;
            }

            if(argument.getArgModifier() == RMethodArgument.ArgModifier.keyrest)
            {
                keyrest = true;
                keyflag = true;
            }

            if(argument.getArgModifier() == RMethodArgument.ArgModifier.rest)
            {
                restPosition = i;
            }
        }
        for(int i = 0; i < myArgsInfo.size(); i++)
        {
            RMethodArgument argument = myArgsInfo.get(i);
            String argumentName = argument.getName();

            if(argument.getArgModifier() == RMethodArgument.ArgModifier.req)
            {
                updateArgExists(i);
            }
        }

        if(myKeyWords.size() == 0){
            for(int i = 0; i < myArgsInfo.size(); i++)
            {
                RMethodArgument argument = myArgsInfo.get(i);
                String argumentName = argument.getName();

                if(argument.getArgModifier() == RMethodArgument.ArgModifier.opt) {
                    updateArgExists(i);
                }
            }
        }
        else
        {
            if(!keyflag)
            {
                for(int i = 0; i < myArgsInfo.size(); i++)
                {
                    RMethodArgument argument = myArgsInfo.get(i);
                    String argumentName = argument.getName();

                    if(argument.getArgModifier() == RMethodArgument.ArgModifier.opt) {
                        updateArgExists(i);
                    }
                }
            }
            else
            {
                for(int i = 0; i < myArgsInfo.size(); i++)
                {
                    RMethodArgument argument = myArgsInfo.get(i);
                    String argumentName = argument.getName();

                    if(argument.getArgModifier() == RMethodArgument.ArgModifier.key) {
                        if(myKeyWords.contains(argumentName)) {
                            argument.setIsGiven(true);
                            myCallArgc--;
                            myKeyWords.remove(argumentName);
                        }

                    }
                    if(argument.getArgModifier() == RMethodArgument.ArgModifier.keyreq) {
                        if(myKeyWords.contains(argumentName)){
                            argument.setIsGiven(true);
                            myCallArgc--;
                            myKeyWords.remove(argumentName);
                        }
                    }
                    if(argument.getArgModifier() == RMethodArgument.ArgModifier.keyrest) {
                        for (String kwName : myKeyWords) {
                            argument.setIsGiven(true);
                            myCallArgc--;
                        }
                        myKeyWords.clear();
                    }
                }
            }
        }

        for(int i = 0; i < myArgsInfo.size(); i++)
        {
            RMethodArgument argument = myArgsInfo.get(i);
            String argumentName = argument.getName();

            if(argument.getArgModifier() == RMethodArgument.ArgModifier.rest)
            {
                if(this.getCallArgc() > 0)
                {
                    argument.setIsGiven(true);
                    myCallArgc = 0;
                    for (String kwName : myKeyWords) {
                        argument.addInfo(kwName);
                        //this.myCallArgc--;
                    }
                    myKeyWords.clear();
                }
            }
        }

        if(myCallArgc != 0 || myKeyWords.size() != 0)
        {
            System.out.println("Incorrect ArgC");
        }
        for (RMethodArgument argument : myArgsInfo) {
            if(argument.getArgModifier() == RMethodArgument.ArgModifier.req && !argument.getIsGiven())
            {
                System.out.println("Req not given");
                break;
            }
            if(argument.getArgModifier() == RMethodArgument.ArgModifier.keyreq && !argument.getIsGiven())
            {
                System.out.println("Keyreq not given");
                break;
            }
        }

        //RMethodArgument argument = myArgsInfo.get(restPosition);
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

    public List<String> getArgsTypeName() {
        return myArgsTypeName;
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

    public List<String> getKeyWords(){
        return this.myKeyWords;
    }

    public String getGemVersion() {
        return myGemVersion;
    }

    public String getReturnTypeName() {
        return myReturnTypeName;
    }

    public void setReturnTypeName(final String returnTypeName) {
        this.myReturnTypeName = returnTypeName;
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
