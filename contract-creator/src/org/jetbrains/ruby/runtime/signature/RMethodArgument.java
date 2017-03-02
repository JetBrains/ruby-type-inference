package org.jetbrains.ruby.runtime.signature;

import org.jetbrains.ruby.codeInsight.types.signature.ParameterInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Created by viuginick on 12/2/16.
 */

public class RMethodArgument {
    private String myName;
    private ParameterInfo.Type myModifier;
    private String myType;
    private Boolean isGiven;
    private List<String> additionalInfo;

    public RMethodArgument(String argument)
    {
        List<String> parts = Arrays.asList(argument.split("\\s*,\\s*"));
        this.myName = parts.get(1);
        this.myModifier = ParameterInfo.Type.valueOf(parts.get(0).toUpperCase(Locale.US));
        this.myType = parts.get(2);
        this.isGiven = false;
        this.additionalInfo = new ArrayList<>();
    }

    public String getName()
    {
        return this.myName;
    }

    public String getType()
    {
        return this.myType;
    }

    public void addInfo(String additionalInfo)
    {
        this.additionalInfo.add(additionalInfo);
    }
    public List<String> getAdditionalInfo()
    {
        return this.additionalInfo;
    }

    public ParameterInfo.Type getArgModifier()
    {
        return this.myModifier;
    }

    public Boolean getIsGiven()
    {
        return this.isGiven;
    }

    public void setIsGiven(Boolean newVal)
    {
        this.isGiven = newVal;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RMethodArgument that = (RMethodArgument) o;

        return myName.equals(that.myName) &&
                myModifier.equals(that.myModifier) &&
                myType.equals(that.myType) &&
                isGiven.equals(that.isGiven) &&
                additionalInfo.equals(that.additionalInfo);
    }

    @Override
    public int hashCode() {
        int result = myName.hashCode();
        result = 31 * result + myModifier.hashCode();
        result = 31 * result + myType.hashCode();
        result = 31 * result + isGiven.hashCode();
        result = 31 * result + additionalInfo.hashCode();

        return result;
    }
}

