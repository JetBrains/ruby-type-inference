package org.jetbrains.ruby.runtime.signature;

public class RMethodInfo {
    private final String myMethodName;
    private final String myReceiverName;
    private final String myGemName;
    private final String myGemVersion;
    private final String myVisibility;
    private final String myPath;
    private final Integer myLineNumber;

    public RMethodInfo(RSignature signature) {
        this.myMethodName = signature.getMethodName();
        this.myReceiverName = signature.getReceiverName();
        this.myGemName = signature.getGemName();
        this.myGemVersion = signature.getGemVersion();
        this.myVisibility = signature.getVisibility();
        this.myPath = signature.getPath();
        this.myLineNumber = signature.getLineNumber();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RMethodInfo that = (RMethodInfo) o;

        return myMethodName.equals(that.myMethodName) &&
                myReceiverName.equals(that.myReceiverName) &&
                myGemName.equals(that.myGemName) &&
                myGemVersion.equals(that.myGemVersion) &&
                myVisibility.equals(that.myVisibility) &&
                myPath.equals(that.myPath) &&
                myLineNumber.equals(that.myLineNumber);

    }

    public String toString()
    {
        return "MethodName: " + this.getMethodName() + "\n" +
               "ReceiverName: " + this.getReceiverName() + "\n" +
               "GemName: " + this.getGemName() + "\n" +
               "GemVersion: " + this.getGemVersion() + "\n" +
               "Visibility: " + this.getVisibility();
    }

    public String getMethodName()
    {
        return this.myMethodName;
    }

    public String getReceiverName()
    {
        return this.myReceiverName;
    }

    public String getGemName()
    {
        return this.myGemName;
    }


    public String getGemVersion()
    {
        return this.myGemVersion;
    }
    public String getVisibility()
    {
        return this.myVisibility;
    }

    public String getPath()
    {
        return this.myPath;
    }
    public int getLine()
    {
        return this.myLineNumber;
    }


    @Override
    public int hashCode() {
        int result = myMethodName.hashCode();
        result = 31 * result + myReceiverName.hashCode();
        result = 31 * result + myGemName.hashCode();
        result = 31 * result + myVisibility.hashCode();
        result = 31 * result + myGemVersion.hashCode();
        result = 31 * result + myPath.hashCode();
        result = 31 * result + myLineNumber.hashCode();
        return result;
    }
}
