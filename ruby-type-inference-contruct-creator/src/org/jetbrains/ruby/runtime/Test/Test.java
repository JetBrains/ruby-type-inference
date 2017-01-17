package org.jetbrains.ruby.runtime.Test;

import org.jetbrains.ruby.runtime.signature.RSignature;
import org.jetbrains.ruby.runtime.signature.RSignatureContract;

import java.util.ArrayList;
import java.util.List;


public class Test {
    public static void main(String [] args)
    {
        List<RSignature> inputSignatures = new ArrayList<>();

        List<String> sig1 = new ArrayList<>();
        sig1.add("Int");
        sig1.add("String");
        sig1.add("Int");
        sig1.add("Int");

        List<String> sig2 = new ArrayList<>();
        sig2.add("String");
        sig2.add("Int");
        sig2.add("Int");
        sig2.add("Int");

        //inputSignatures.add(new RSignature("test", "test", sig1, "test", "test", "Int", "PRIVATE"));
        //inputSignatures.add(new RSignature("test", "test", sig2, "test", "test", "Int", "PRIVATE"));

        RSignatureContract answer = new RSignatureContract(inputSignatures);
        answer.minimization();
        List<String> answerToS = answer.getStringPresentation();
        for (String answerS : answerToS) {
            System.out.println(answerS);
        }
    }
}
