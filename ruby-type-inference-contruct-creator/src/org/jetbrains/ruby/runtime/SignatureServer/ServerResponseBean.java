package org.jetbrains.ruby.runtime.SignatureServer;

public class ServerResponseBean {
    public String method_name;
    public String receiver_name;
    public String gem_name;
    public String gem_version;
    public String visibility;
    public String return_type_name;
    public int call_info_argc;
    public String args_type_name;
    public String args_info;
    public String call_info_kw_args;
    public String path;
    public int lineno;
}
