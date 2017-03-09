package org.jetbrains.ruby.runtime.signature.server;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ruby.codeInsight.types.signature.*;
import org.jetbrains.ruby.runtime.signature.RawSignature;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Logger;


public class SignatureServer {
    private static final Logger LOGGER = Logger.getLogger("SignatureServer");

    RSignatureContractContainer mainContainer = new RSignatureContractContainer();

    @Nullable
    private static SignatureServer ourInstance;

    @NotNull
    public static SignatureServer getInstance() {
        if (ourInstance == null) {
            ourInstance = new SignatureServer();
        }

        return ourInstance;
    }

    @Nullable
    public RSignatureContract getContract(@NotNull MethodInfo info) {
        return mainContainer.getSignature(info);
    }

    @Nullable
    public RSignatureContract getContractByMethodName(@NotNull String methodName) {

        for (MethodInfo info : mainContainer.getKeySet()) {
            if (info.getName().equals(methodName))
                return mainContainer.getSignature(info);
        }
        return null;
    }

    @Nullable
    public RSignatureContract getContractByMethodInfo(@NotNull MethodInfo info) {
        return mainContainer.getSignature(info);
    }


    public void runServer() throws IOException {
        LOGGER.info("Starting server");

        Path file = Paths.get("callStatLog.txt");

        try (ServerSocket listener = new ServerSocket(7777)) {
            //noinspection InfiniteLoopStatement
            while (true) {
                Socket socket = listener.accept();
                BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String currString;

                while ((currString = br.readLine()) != null) {
                    try {
                        if (currString.equals("break connection")) {
                            mainContainer.print(file);
                            break;
                        }

                        ServerResponseBean result = new Gson().fromJson(currString, ServerResponseBean.class);

                        if (result != null) {
                            RawSignature currRawSignature = new RawSignature(result);
                            RSignature currRSignature = currRawSignature.getRSignature();

                            if (!result.call_info_mid.equals("nil")) {
                                RSignatureFetcher fetcher = new RSignatureFetcher(currRawSignature.argc, currRawSignature.kwArgs);
                                List<Boolean> flags = fetcher.fetch(currRSignature.getArgsInfo());

                                for (int i = 0; i < flags.size(); i++) {
                                    Boolean flag = flags.get(i);
                                    if (!flag)
                                        currRawSignature.changeArgumentType(i, "-");
                                }
                            }

                            if (result.method_name.equals("initialize") || result.call_info_mid.equals("send") || result.call_info_mid.equals("nil") || result.call_info_mid.equals(result.method_name)) {
                                mainContainer.addSignature(currRSignature);
                            }

                        }

                    } catch (JsonParseException e) {
                        LOGGER.severe("!" + currString + "!\n" + e);
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}