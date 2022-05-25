package module.router.schema;

import module.AbuildCore;
import module.router.ISchemaAction;
import module.server.EmbedHttpServer;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by huangyong on 16/7/28.
 */
public class RestartSchema implements ISchemaAction {

    @Override
    public String getDescription() {
        return "restart";
    }

    @Override
    public void handle(String method, String path, HashMap<String, String> headers, Map<String, String> queries, InputStream input, EmbedHttpServer.ResponseOutputStream response) throws Exception {
        AbuildCore.restartApplication(null, null, null, null);
        response.setStatusCode(200);
    }
}
