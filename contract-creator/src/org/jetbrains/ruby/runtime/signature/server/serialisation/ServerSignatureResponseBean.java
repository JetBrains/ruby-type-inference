package org.jetbrains.ruby.runtime.signature.server.serialisation;

@SuppressWarnings("InstanceVariableNamingConvention")
class ServerSignatureResponseBean {
    String method_name;
    String call_info_mid;
    String receiver_name;
    String gem_name;
    String gem_version;
    String visibility;
    String return_type_name;
    String call_info_argc;
    String args_type_name;
    String args_info;
    String call_info_kw_args;
    String path;
    int lineno;
}