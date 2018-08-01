package org.jetbrains.ruby.runtime.signature.server.serialisation;

@SuppressWarnings("InstanceVariableNamingConvention")
class ServerResponseBean {
    String method_name;
    String call_info_mid;
    String receiver_name;
    String visibility;
    String return_type_name;
    String call_info_argc;
    String args_type_name;
    String args_info;
    String call_info_kw_args;
    String path;
    int lineno;
}