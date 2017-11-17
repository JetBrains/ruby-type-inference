package org.jetbrains.ruby.runtime.signature.server.serialisation;

import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ruby.codeInsight.types.signature.*;

public class MethodCachedNameBuilder {
    private static final Gson GSON = new Gson();

    static private String beautifyClassName(String bean) {
        if (bean.length() > 90) {
            return bean.substring(0, 90) + "...";
        }
        return bean;
    }

    @Nullable
    public static RCachedMethod fromJson(@NotNull String json) {
        final ServerMethodInfoResponseBean result = GSON.fromJson(json, ServerMethodInfoResponseBean.class);
        final MethodInfo methodInfo = MethodInfoKt.MethodInfo(
                ClassInfoKt.ClassInfo(GemInfoKt.GemInfoOrNull(result.gem_name, result.gem_version), beautifyClassName(result.receiver_name)),
                result.method_name,
                //TODO hohohaha
                RVisibility.valueOf("PUBLIC"),
                new Location(result.path, result.lineno));



        return RCachedMethodKt.RCachedMethod(methodInfo, result.id, result.param_info);
    }
}
