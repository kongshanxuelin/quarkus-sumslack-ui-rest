package org.acme.dto;

public class ExecResponse {
    public Object data;
    public String status = "200";
    public String message;
    public Object error;

    public static ExecResponse success(Object data) {
        ExecResponse resp = new ExecResponse();
        resp.error = null;
        resp.data = data;
        resp.status = "200";
        resp.message = "OK";
        return resp;
    }

    public static ExecResponse error(Object error) {
        ExecResponse resp = new ExecResponse();
        resp.error = error;
        resp.data = null;
        resp.status = "500";
        resp.message = "ERROR";
        return resp;
    }
}
