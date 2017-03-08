package org.jetbrains.plugins.ruby.ruby.codeInsight;

import org.jetbrains.ruby.runtime.signature.server.SignatureServer;


public class SignatureService {

    public SignatureService() {
        new Thread() {
            @Override
            public void run() {
                SignatureServer server = SignatureServer.getInstance();
                try {
                    server.runServer();
                } catch (Exception e) {

                }

            }
        }.start();
    }

}
