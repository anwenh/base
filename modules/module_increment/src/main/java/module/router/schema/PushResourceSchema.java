package module.router.schema;

import android.os.Build;
import android.util.Log;

import module.AbuildCore;
import module.router.ISchemaAction;
import module.server.EmbedHttpServer;
import module.server.LongLinkServer;
import module.util.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by huangyong on 16/7/28.
 */
public class PushResourceSchema implements ISchemaAction {

    private static final String TAG = "Freeline.PushResource";

    @Override
    public String getDescription() {
        return "pushResource";
    }

    @Override
    public void handle(String method, String path, HashMap<String, String> headers, Map<String, String> queries, InputStream input, EmbedHttpServer.ResponseOutputStream response) throws Exception {
        Log.d(TAG, new StringBuilder().append("receive res ").append(path).toString());
        String bundleName = queries.get("bundleId");

        boolean fullBuild = Build.VERSION.SDK_INT <= 19;

        if (fullBuild) {
            Log.d(TAG, "receive full res pck command");
        }

        Log.d(TAG, new StringBuilder().append("bundle id ").append(bundleName).toString());
        String destPath = AbuildCore.getDynamicInfoTempPath(bundleName);
        File lastPathFile = new File(AbuildCore.getBundleFilePathByPackageId(bundleName));
        Log.i(TAG, new StringBuilder().append("bundle last time :").append(lastPathFile.lastModified()).toString());
        File destFile = new File(destPath);
        File pendingFile = new File(destPath + ".bak");
        int count = 0;
        try {
            if (!pendingFile.exists()) {
                pendingFile.createNewFile();
            }
            FileOutputStream fos = new FileOutputStream(pendingFile);
            byte[] buf = new byte[4096];
            int l;
            while ((l = input.read(buf)) != -1) {
                fos.write(buf, 0, l);
                count += l;
            }
            fos.close();
        } catch (Exception e) {
            Log.d(TAG, e.getStackTrace().toString());
            response.setStatusCode(500);
            return;
        }
        if (fullBuild) {
            FileUtils.rm(destFile);
            boolean rst2 = pendingFile.renameTo(destFile);
            Log.d(TAG, new StringBuilder().append("delete pending file rename rst :").append(rst2).toString());
        } else {
            Log.i(TAG, "anwen "+destFile.getAbsolutePath());

            if (destFile.exists()) {
                Log.i(TAG, "anwen destFile exist"+destFile.isDirectory());
                if (!destFile.isDirectory()) {
                    File temp = new File(new StringBuilder().append(destFile.getAbsolutePath()).append(".temp").toString());
                    boolean rst = destFile.renameTo(temp);
                    Log.d(TAG, new StringBuilder().append("dest File renameTo ").append(temp.getAbsolutePath()).append(" rst:").append(rst).toString());
                    destFile = new File(destPath);

                    Log.i(TAG, "anwen destFile rst"+rst+" temp"+temp.getAbsolutePath());
                    if (rst) {
                        long s = System.currentTimeMillis();
                        destFile.mkdirs();
                        FileUtils.unzip(temp, destFile);
                        Log.d(TAG, new StringBuilder().append("unzip file ").append(temp).append(" to ").append(destPath).append(" rst:").append(rst).append(" last:").append(System.currentTimeMillis() - s).toString());
                        Log.d(TAG, new StringBuilder().append("after unzip ,dir file size =").append(destFile.list().length).toString());
                    }
                }
            } else {
                try {
                    long s = System.currentTimeMillis();
                    destFile.mkdirs();
                    FileUtils.unzip(lastPathFile, destFile);
                    Log.d(TAG, new StringBuilder().append("unzip old file ").append(lastPathFile).append(" to  ").append(destFile.getAbsolutePath()).append(" last:").append(System.currentTimeMillis() - s).toString());
                    Log.d(TAG, new StringBuilder().append("after unzip ,dir file size =").append(destFile.list().length).toString());
                } catch (Exception e) {
                    AbuildCore.printStackTrace(e);
                }
            }
            long s = System.currentTimeMillis();
            FileUtils.unzip(pendingFile, destFile);
            FileUtils.rm(pendingFile);
            Log.d(TAG, new StringBuilder().append("sync res increment files to  ").append(destFile.getAbsolutePath()).append(" last:").append(System.currentTimeMillis() - s).toString());
        }
        //FreelineCore.saveDynamicInfo(bundleName, destPath);
        LongLinkServer.setBundleName(bundleName);
        LongLinkServer.setDstPath(destPath);
        LongLinkServer.markResourcesChanged();
        response.setStatusCode(201);
        Log.d(TAG, new StringBuilder().append("increment resources file received (").append(count).append(" bytes)").toString());
    }
}
