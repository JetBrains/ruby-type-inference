package org.jetbrains.ruby.runtime.signature.server.serialisation;

import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;

public class MethodCachedNameBuilder {
    private static final Gson GSON = new Gson();

    @NotNull
    public static ServerMethodNameResponseBean fromJson(@NotNull String json) {
        return GSON.fromJson(json, ServerMethodNameResponseBean.class);
    }
}
