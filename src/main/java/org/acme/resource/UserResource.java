package org.acme.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.acme.entity.Test23;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/node/api")
@Produces(MediaType.APPLICATION_JSON)
public class UserResource {

    //TODO:这里检验用户名和密码，我这里统一返回登录验证通过的包
    public static final Map resultMockMap = new HashMap();
    public static final  Map userMap = new HashMap();
    static {
        userMap.put("userId", "test");
        userMap.put("username", "test");
        userMap.put("access_token", "test");
        userMap.put("gatewayToken", "test");
        resultMockMap.put("status", 200);
        resultMockMap.put("success", true);
        resultMockMap.put("retCode", "");
        resultMockMap.put("content", userMap);
    }
    @GET
    @Path("/user")
    public Map user() {
        return resultMockMap;
    }

    @POST
    @Path("/user/login")
    public Map login(Map<String, String> request) {
//        return Test23.find("test2 like ?1", "%a%").page(0, 10).list();
        String username = request.put("username","");
        String password = request.put("password","");
        return resultMockMap;
    }

    @GET
    @Path("/count")
    public long count() {
//        return Test23.count();
        return 0;
    }
}
