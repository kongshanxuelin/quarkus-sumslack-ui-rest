package org.acme.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.dto.ExecRequest;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ScriptExecutor {

    @Inject
    SQLiteHelper db;

    public Object execute(String scriptCode, String scriptType, ExecRequest params) {
        try (Context context = Context.newBuilder(scriptType.toLowerCase())
                .allowHostAccess(HostAccess.newBuilder(HostAccess.EXPLICIT)
                        .allowMapAccess(true)
                        .allowListAccess(true)
                        .allowArrayAccess(true)
                        .build())
                .allowHostClassLookup(className -> false)
                .allowIO(false)
                .allowCreateThread(false)
                .allowNativeAccess(false)
                .allowCreateProcess(false)
                .build()) {

            Value bindings = context.getBindings(scriptType.toLowerCase());

            // 将 ExecRequest 转换为 Map，以便脚本可以访问
            Map<String, Object> paramsMap = convertRequestToMap(params);
            bindings.putMember("params", paramsMap);
            bindings.putMember("db", db);

            Value result = context.eval(scriptType.toLowerCase(), scriptCode);

            return convertValue(result);

        } catch (Exception e) {
            throw new RuntimeException("脚本执行失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> convertRequestToMap(ExecRequest request) {
        Map<String, Object> map = new HashMap<>();
        if (request != null) {
            map.put("params", request.params != null ? request.params : new HashMap<>());
            map.put("pageSize", request.pageSize);
            map.put("pageNum", request.pageNum);
        }
        return map;
    }

    private Object convertValue(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }

        if (value.isHostObject()) {
            Object hostObject = value.asHostObject();
            return convertJavaObject(hostObject);
        }

        if (value.isBoolean()) {
            return value.asBoolean();
        }

        if (value.isNumber()) {
            if (value.fitsInInt()) {
                return value.asInt();
            } else if (value.fitsInLong()) {
                return value.asLong();
            } else {
                return value.asDouble();
            }
        }

        if (value.isString()) {
            return value.asString();
        }

        if (value.hasArrayElements()) {
            List<Object> list = new ArrayList<>();
            for (long i = 0; i < value.getArraySize(); i++) {
                list.add(convertValue(value.getArrayElement(i)));
            }
            return list;
        }

        if (value.hasMembers()) {
            Map<String, Object> map = new HashMap<>();
            for (String key : value.getMemberKeys()) {
                map.put(key, convertValue(value.getMember(key)));
            }
            return map;
        }

        return value.toString();
    }

    private Object convertJavaObject(Object obj) {
        if (obj == null) {
            return null;
        }

        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                result.add(convertJavaObject(item));
            }
            return result;
        }

        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                result.put(key, convertJavaObject(entry.getValue()));
            }
            return result;
        }

        if (obj instanceof String || obj instanceof Number || obj instanceof Boolean) {
            return obj;
        }

        return obj.toString();
    }
}
