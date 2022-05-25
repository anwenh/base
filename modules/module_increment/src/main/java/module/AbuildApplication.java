package module;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.util.Log;

import module.util.ReflectUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Constructor;

/**
 * Created by huangyong on 16/9/14.
 */

public class AbuildApplication extends Application {

    protected static final String TAG = "AbuildApplication";

    private Class abuildConfigClazz;

    private Application realApplication;
    public static String getProcessName() {
        try {
            File file = new File("/proc/" + android.os.Process.myPid() + "/" + "cmdline");
            BufferedReader mBufferedReader = new BufferedReader(new FileReader(file));
            String processName = mBufferedReader.readLine().trim();
            mBufferedReader.close();
            return processName;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    @Override
    public void onCreate() {
        super.onCreate();

        String processName = AbuildApplication.getProcessName();

        //判断进程名，保证只有主进程运行
        if (!TextUtils.isEmpty(processName) &&processName.equals(this.getPackageName())) {
            //在这里进行主进程初始化逻辑操作
            Log.i(">>>>>>","oncreate");
        }
        initFreelineConfig();
        createRealApplication();
        AbuildCore.init(this, realApplication);
        startRealApplication();
    }

    @Override
    public Context createPackageContext(String packageName, int flags) throws PackageManager.NameNotFoundException {
        Context c = realApplication.createPackageContext(packageName, flags);
        return c == null ? realApplication : c;
    }

    private void startRealApplication() {
        if (realApplication != null) {
            try {
                ReflectUtil.invokeMethod(Application.class, realApplication, "attach", new Class[]{Context.class}, new Object[]{getBaseContext()});
                Log.d(TAG, "realApplication#attach(Context)");
            } catch (Exception e) {
                AbuildCore.printStackTrace(e);
                Log.e(TAG, "attach with realApplication error");
            }

            realApplication.onCreate();
            Log.d(TAG, "realApplication#onCreate()");
        }
    }

    private void initFreelineConfig() {
        try {
            abuildConfigClazz = Class.forName("com.antfortune.freeline.FreelineConfig");
        } catch (Exception e) {
            AbuildCore.printStackTrace(e);
            Log.e(TAG, "initFreelineConfig error");
        }
    }

    private String getConfigValue(String fieldName) {
        try {
            return ReflectUtil.getStaticFieldValue(abuildConfigClazz, fieldName).toString();
        } catch (Exception e) {
            AbuildCore.printStackTrace(e);
            Log.e(TAG, "get config value error");
            return "";
        }
    }

    private void createRealApplication() {
        String applicationClass = getConfigValue("applicationClass");
        if (TextUtils.isEmpty(applicationClass)) {
            realApplication = new Application();
            Log.d(TAG, "create empty application.");
        } else {
            try {
                Class realClass = Class.forName(applicationClass);
                Constructor<? extends Application> constructor = realClass.getConstructor();
                this.realApplication = constructor.newInstance();
                Log.d(TAG, "create application: " + applicationClass);
            } catch (Exception e) {
                AbuildCore.printStackTrace(e);
                Log.e(TAG, "create real application error");
            }
        }
    }

}
