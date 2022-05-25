package module;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;

public class MiddlewareActivity extends Activity {
    private static final String TAG = "Freeline.MiddlewareAct";
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static final long RESET_WAIT = 1000L;
    private static final int DELAY_TIME = 200;
    private long createTime;
    private boolean ready;
    private int back;
    private final Runnable reset = new Runnable() {
        public void run() {

            //通过定时器启动提高重新启动应用的成功率
            Context context = MiddlewareActivity.this;

            //保留activity堆栈
//            Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
//            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
//
//            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
//
//            AlarmManager mgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
//            mgr.set(AlarmManager.RTC, System.currentTimeMillis() + DELAY_TIME, pendingIntent);

            Log.d(TAG, "kill process: " + Process.myPid());
            Process.killProcess(Process.myPid());//android 重启进程 同时保留栈 这是系统决定的.
        }

    };

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setTextColor(Color.BLUE);
        tv.setGravity(17);
        tv.setText("building increment app...");
        setContentView(tv);
        this.createTime = SystemClock.uptimeMillis();
        Log.d(TAG, "onCreate called" + Process.myPid());

        this.ready = getIntent().getBooleanExtra("reset", false);
        boolean callReset = AbuildCore.getSyncInfoSp().getBoolean("callReset", false);
        if (callReset) {//主动调用方法进入的这里.
            //防止重复启动
            AbuildCore.getSyncInfoSp().edit().putBoolean("callReset", false).commit();
        } else {
            ready = false;
            finish();
        }

        if (this.ready) {
            reset();
        }
    }


    protected void onDestroy() {
        HANDLER.removeCallbacks(this.reset);
        super.onDestroy();
    }


    public void reset() {
        this.ready = true;
        HANDLER.removeCallbacks(this.reset);
        long d = SystemClock.uptimeMillis() - this.createTime;
        if (d > RESET_WAIT) {
            HANDLER.postDelayed(this.reset, 100L);
        } else {
            HANDLER.postDelayed(this.reset, RESET_WAIT - d);
        }
    }


    public void onBackPressed() {
//        if (this.back++ > 0) {
//            if (this.ready) {
//                this.reset.run();
//            }
            super.onBackPressed();
//        }
    }

}
