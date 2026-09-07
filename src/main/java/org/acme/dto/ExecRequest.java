package org.acme.dto;

import jakarta.json.*;

import java.util.*;

public class ExecRequest {
    public Map<String, Object> params;
    public Integer pageSize;
    public Integer pageNum;

    @Override
    public String toString() {
        Map<String, Object> result = new HashMap<>();
        result.put("params",params);
        result.put("pageSize",pageSize);
        result.put("pageNum",pageNum);
        return result.toString();
    }
}
