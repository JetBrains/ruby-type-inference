package SignatureServer;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import signature.RSignature;
import signature.RSignatureContract;
import signature.RSignatureContractContainer;

public class SignatureServer {

    public static void main(String[] args) throws IOException {
        ServerSocket listener = new ServerSocket(7777);

        RSignatureContractContainer mainContainer = new RSignatureContractContainer();

        try {
            while (true) {
                Socket socket = listener.accept();
                BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String currString;

                while ((currString = br.readLine()) != null) {
                    try {
                        System.out.println(currString);
                        if(currString.equals("break connection")){

                            return;
                        }
                        JSONObject json = (JSONObject) new JSONParser().parse(currString);
                        RSignature currSignature = new RSignature(json);

                        mainContainer.addSignature(currSignature);
                        //System.out.println("Done " + mainContainer.getContractCount());

//                        if(mainContainer.getContractCount() > 2000)
//                        {
//                            return;
//                        }


                    } catch (ParseException e) {
                        System.out.println("!" + currString + "!");
                        e.printStackTrace();
                    }
                }
            }
        }
        finally {
            mainContainer.print();
            listener.close();

        }
    }
}