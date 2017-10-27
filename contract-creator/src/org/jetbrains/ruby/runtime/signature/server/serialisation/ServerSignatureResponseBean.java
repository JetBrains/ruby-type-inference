package org.jetbrains.ruby.runtime.signature.server.serialisation;

@SuppressWarnings("InstanceVariableNamingConvention")
class ServerSignatureResponseBean {
    int method_id;
    String args_info;
    String return_type_name;

    String call_info_argc;
    String call_info_kw_args;
}