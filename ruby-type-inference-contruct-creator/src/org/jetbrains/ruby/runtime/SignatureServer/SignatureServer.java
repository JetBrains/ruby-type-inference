package org.jetbrains.ruby.runtime.SignatureServer;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import org.jetbrains.ruby.runtime.signature.RSignature;
import org.jetbrains.ruby.runtime.signature.RSignatureContractContainer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class SignatureServer {

    public static void main(String[] args) throws IOException {

        RSignatureContractContainer mainContainer = new RSignatureContractContainer();

        try (ServerSocket listener = new ServerSocket(7777)) {
            while (true) {
                Socket socket = listener.accept();
                BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String currString;

                while ((currString = br.readLine()) != null) {
                    try {
                        System.out.println(currString);
                        if (currString.equals("break connection")) {
                            return;
                        }

                        ServerResponseBean result = new Gson().fromJson(new InputStreamReader(socket.getInputStream()), ServerResponseBean.class);
                        RSignature currSignature = new RSignature(result);

                        mainContainer.addSignature(currSignature);
                        //System.out.println("Done " + mainContainer.getContractCount());

//                        if(mainContainer.getContractCount() > 2000)
//                        {
//                            return;
//                        }


                    } catch (JsonParseException e) {
                        System.out.println("!" + currString + "!");
                        e.printStackTrace();
                    }
                }
            }
        } finally {
            mainContainer.print();
        }
    }
}