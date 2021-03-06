package module;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;

import module.util.ActivityManager;

import java.util.HashMap;

/**
 * Created by huangyong on 16/7/31.
 */
public class AbuildReceiver extends BroadcastReceiver {

    private static final String TAG = "Freeline.Receiver";

    public static final String ACTION_KEY = "action";

    public static final String UUID = "uuid";

    public static final String SP_KEY = "sp_key";
    public static final String SP_VALUE = "sp_value";
    public static final String DEX_VALUE = "dex_path";
    public static final String OPT_VALUE = "opt_path";

    public static final int ACTION_UPDATE_ACTIVITY = 1;
    public static final int ACTION_RESTART_APPLICATION = 2;
    public static final int ACTION_SAVE_DYNAMIC_INFO = 3;

    @Override
    public void onReceive(Context context, Intent intent) {
        int type = intent.getIntExtra(ACTION_KEY, -1);
        String processName = AbuildApplication.getProcessName();

        Log.i(TAG, "receive action type: " + type+" processName"+processName);
        if (type == ACTION_UPDATE_ACTIVITY) {
            saveDynamicResInfo(intent);
            ActivityManager.restartForegroundActivity();
        } else if (type == ACTION_RESTART_APPLICATION) {
            saveDynamicResInfo(intent);
            applyDynamicDex(intent);
            ActivityManager.restart(AbuildCore.getApplication(), true);
        }
    }

    private void saveDynamicResInfo(Intent intent) {
        String key = intent.getStringExtra(SP_KEY);
        String value = intent.getStringExtra(SP_VALUE);
        if (!TextUtils.isEmpty(key) && !TextUtils.isEmpty(value)) {
            HashMap<String, String> res = new HashMap<String, String>();
            Log.d(TAG, "destPath :" + value);
            res.put(key, value);
            AbuildCore.saveDynamicResInfo(res);
        }
    }

    private void applyDynamicDex(Intent intent) {
        String dex = intent.getStringExtra(DEX_VALUE);
        String opt = intent.getStringExtra(OPT_VALUE);
        if (!TextUtils.isEmpty(dex) && !TextUtils.isEmpty(opt)) {
            AbuildCore.applyDynamicDex(dex, opt);
        }
    }

}
