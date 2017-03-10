package org.jetbrains.plugins.ruby.ruby.codeInsight;

import org.jetbrains.ruby.runtime.signature.server.SignatureServer;

import java.util.logging.Logger;


public class SignatureService {
    private static final Logger LOGGER = Logger.getLogger("SignatureService");
    public SignatureService() {
        new Thread() {
            @Override
            public void run() {
                SignatureServer server = SignatureServer.getInstance();
                try {
                    server.runServer();
                } catch (Exception e) {
                    LOGGER.severe(e.getMessage());
                    e.printStackTrace();
                }

            }
        }.start();
    }

}
