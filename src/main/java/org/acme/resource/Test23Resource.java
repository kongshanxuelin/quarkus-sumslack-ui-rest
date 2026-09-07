package org.acme.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.acme.entity.Test23;

import java.util.List;

@Path("/api/test23")
@Produces(MediaType.APPLICATION_JSON)
public class Test23Resource {

    @GET
    public List<Test23> listAll() {
//        return Test23.find("test2 like ?1", "%a%").page(0, 10).list();
        return null;
    }

    @GET
    @Path("/count")
    public long count() {
//        return Test23.count();
        return 0;
    }
}
