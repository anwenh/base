package module.router.schema;

import android.util.Log;

import module.AbuildCore;
import module.router.ISchemaAction;
import module.server.EmbedHttpServer;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by hejie on 17/4/17
 */
public class GetSyncTicketSchema implements ISchemaAction {

    private static final String TAG = "Freeline.GetSyncTicket";

    @Override
    public String getDescription() {
        return "getSyncTicket";
    }

    @Override
    public void handle(String method, String path, HashMap<String, String> headers, Map<String, String> queries, InputStream input, EmbedHttpServer.ResponseOutputStream response) throws Exception {
        Log.i(TAG, "GetSyncTicketSchema: " );
        String result = "{'apkBuildFlag':"+0+",'lastSync':"+0+",'packageName':'"+ AbuildCore.getApplication().getPackageName()+"'}";
        response.setContentTypeText();
        response.write(result.getBytes("utf-8"));
    }


}
