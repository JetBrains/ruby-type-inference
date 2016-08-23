package org.jetbrains.plugins.ruby.ruby.codeInsight.types;

import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.ArgumentInfo;

class ArgumentInfoWithValue extends ArgumentInfo {
    @NotNull
    private String myValueTypeName;

    ArgumentInfoWithValue(@NotNull final String name, @NotNull final Type type, @NotNull final String valueTypeName) {
        super(StringRef.fromString(name), type);
        this.myValueTypeName = valueTypeName;
    }

    ArgumentInfoWithValue(@NotNull final ArgumentInfoWithValue argInfo) {
        this(argInfo.getName(), argInfo.getType(), argInfo.getValueTypeName());
    }

    @NotNull
    String getValueTypeName() {
        return myValueTypeName;
    }

    void setValueTypeName(@NotNull final String valueTypeName) {
        this.myValueTypeName = valueTypeName;
    }
}
