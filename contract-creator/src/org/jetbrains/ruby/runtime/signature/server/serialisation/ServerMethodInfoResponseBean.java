package org.jetbrains.ruby.runtime.signature.server.serialisation;

@SuppressWarnings("InstanceVariableNamingConvention")
class ServerMethodInfoResponseBean {
    int id;
    String method_name;
    String receiver_name;
    String param_info;

    String gem_name;
    String gem_version;
    String path;
    int lineno;
}