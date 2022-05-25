package module;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;



import module.router.Router;
import module.server.LongLinkServer;

import java.util.concurrent.TimeUnit;
public class AbuildService extends Service {

    private static final String LOG_TAG = "Abuild.Service";
    private static final String ACTION_KEEP_LIVE = ".Notification_RTC_WAKEUP_PUSH";

    private AlarmManager am = null;
    private PendingIntent mCheckSender = null;

    @Override
    public void onCreate() {
        super.onCreate();
        this.am = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(LOG_TAG, "onStartCommand Received start id " + startId + ", intent: " + intent);
        LongLinkServer.start(this.getApplication(), Router.getInstance());

        String marker = intent == null ? null : intent.getStringExtra("wakeup");
        if (TextUtils.isEmpty(marker)) {
            try {
                setForegroundService();
            } catch (Exception e) {
                Log.e(LOG_TAG, "setForegroundService fail", e);
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            stopForeground(true);
            startAlarmTimer(TimeUnit.SECONDS.toMillis(5));
        } catch (Exception e) {
            Log.e(LOG_TAG, "stopForeground fail", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    protected void startAlarmTimer(long nextTime) {
        Log.i(LOG_TAG, "startAlarmTimer ELAPSED_REALTIME_WAKEUP! nextTime=" + nextTime);

        Intent intent = new Intent();
        intent.setAction(this.getPackageName() + ACTION_KEEP_LIVE);
        this.mCheckSender = PendingIntent.getBroadcast(this, 100, intent, 0);

        try {
            if (am != null && mCheckSender != null) {
                am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + nextTime, mCheckSender);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "startAlarmTimer fail", e);
        }
    }

    private void setForegroundService() {
        Intent nfIntent = new Intent(this, MiddlewareActivity.class);
        Notification.Builder builder = new Notification.Builder(this.getApplicationContext())
                .setContentIntent(PendingIntent.getActivity(this, 0, nfIntent, 0)) // 设置PendingIntent
                .setSmallIcon(android.R.drawable.sym_def_app_icon) // 设置状态栏内的小图标
                .setContentTitle("abuild")
                .setContentText("keep alive service") // 设置上下文内容
                .setWhen(System.currentTimeMillis()); // 设置该通知发生的时间
        String CHANNEL_ONE_ID = "com.primedu.cn";
        String CHANNEL_ONE_NAME = "Channel One";
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            //修改安卓8.1以上系统报错
            NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ONE_ID, CHANNEL_ONE_NAME, NotificationManager.IMPORTANCE_MIN);
            notificationChannel.enableLights(false);//如果使用中的设备支持通知灯，则说明此通知通道是否应显示灯
            notificationChannel.setShowBadge(false);//是否显示角标
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(notificationChannel);
            builder.setChannelId(CHANNEL_ONE_ID);
        }

        Notification notification = builder.build(); // 获取构建好的Notification
        notification.defaults = Notification.DEFAULT_SOUND; //设置为默认的声音
        startForeground(1, notification);
    }
}
