package org.jetbrains.ruby.runtime.signature.server.serialisation;

@SuppressWarnings("InstanceVariableNamingConvention")
public class ServerMethodInfoResponseBean {
    public int id;
    public String method_name;
    public String receiver_name;
    public String param_info;

    public String gem_name;
    public String gem_version;
    public String path;
    public int lineno;
}