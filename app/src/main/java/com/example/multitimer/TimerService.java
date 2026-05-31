package com.example.multitimer;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TimerService extends Service {
    private static final String CHANNEL_ID = "timer_service";
    private static final String CHANNEL_ALARM = "timer_alarm";
    private static final int NOTIFICATION_ID = 1001;
    private static final int ALARM_DURATION_MS = 2 * 60 * 1000; // 2分钟

    public static final String ACTION_START = "com.example.multitimer.START";
    public static final String ACTION_PAUSE = "com.example.multitimer.PAUSE";
    public static final String ACTION_RESET = "com.example.multitimer.RESET";
    public static final String ACTION_DELETE = "com.example.multitimer.DELETE";
    public static final String ACTION_ADD = "com.example.multitimer.ADD";
    public static final String ACTION_RINGTONE = "com.example.multitimer.RINGTONE";
    public static final String ACTION_VOLUME = "com.example.multitimer.VOLUME";
    public static final String ACTION_TICK = "com.example.multitimer.TICK";
    public static final String ACTION_STOP_ALARM = "com.example.multitimer.STOP_ALARM";
    public static final String EXTRA_TIMER_ID = "timer_id";
    public static final String EXTRA_LABEL = "label";
    public static final String EXTRA_SECONDS = "seconds";
    public static final String EXTRA_RINGTONE = "ringtone";
    public static final String EXTRA_VOLUME = "volume";

    private final List<TimerData> timers = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable tickRunnable;
    private PowerManager.WakeLock wakeLock;
    private static TimerService instance;

    // 铃声播放
    private MediaPlayer mediaPlayer;
    private String currentAlarmTimerId;
    private Runnable stopAlarmRunnable;

    public static TimerService getInstance() { return instance; }
    public List<TimerData> getTimers() { return timers; }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannels();
        acquireWakeLock();
        startForeground(NOTIFICATION_ID, buildNotification());
        startTickLoop();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            handleAction(intent);
        }
        return START_STICKY;
    }

    private void handleAction(Intent intent) {
        String action = intent.getAction();
        String id = intent.getStringExtra(EXTRA_TIMER_ID);

        switch (action) {
            case ACTION_ADD: {
                String label = intent.getStringExtra(EXTRA_LABEL);
                long seconds = intent.getLongExtra(EXTRA_SECONDS, 0);
                if (label == null) label = "计时器 " + (timers.size() + 1);
                timers.add(new TimerData(UUID.randomUUID().toString(), label, seconds));
                break;
            }
            case ACTION_START: {
                TimerData t = findTimer(id);
                if (t != null && !t.isRunning() && t.getRemainingSeconds() > 0) {
                    t.setRunning(true);
                    t.setEndTimeMillis(System.currentTimeMillis() + t.getRemainingSeconds() * 1000);
                    scheduleAlarm(t);
                }
                break;
            }
            case ACTION_PAUSE: {
                TimerData t = findTimer(id);
                if (t != null && t.isRunning()) {
                    t.updateRemaining();
                    t.setRunning(false);
                    t.setEndTimeMillis(0);
                    cancelAlarm(t);
                }
                break;
            }
            case ACTION_RESET: {
                TimerData t = findTimer(id);
                if (t != null) {
                    t.setRunning(false);
                    t.setRemainingSeconds(t.getTotalSeconds());
                    t.setEndTimeMillis(0);
                    cancelAlarm(t);
                }
                break;
            }
            case ACTION_DELETE: {
                TimerData t = findTimer(id);
                if (t != null) {
                    cancelAlarm(t);
                    timers.remove(t);
                }
                if (timers.isEmpty()) stopSelf();
                break;
            }
            case ACTION_RINGTONE: {
                TimerData t = findTimer(id);
                if (t != null) t.setRingtoneUri(intent.getStringExtra(EXTRA_RINGTONE));
                break;
            }
            case ACTION_VOLUME: {
                TimerData t = findTimer(id);
                if (t != null) t.setVolume(intent.getIntExtra(EXTRA_VOLUME, 80));
                break;
            }
            case ACTION_STOP_ALARM: {
                stopAlarm();
                break;
            }
        }
        updateNotification();
    }

    private TimerData findTimer(String id) {
        for (TimerData t : timers) {
            if (t.getId().equals(id)) return t;
        }
        return null;
    }

    private void startTickLoop() {
        tickRunnable = new Runnable() {
            @Override
            public void run() {
                for (TimerData t : timers) {
                    if (t.isRunning()) {
                        t.updateRemaining();
                        if (t.getRemainingSeconds() <= 0) {
                            t.setRunning(false);
                            t.setRemainingSeconds(0);
                            t.setEndTimeMillis(0);
                            triggerAlarm(t);
                        }
                    }
                }
                updateNotification();
                handler.postDelayed(this, 200);
            }
        };
        handler.post(tickRunnable);
    }

    private void scheduleAlarm(TimerData timer) {
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent intent = new Intent(this, TimerReceiver.class);
        intent.putExtra(EXTRA_TIMER_ID, timer.getId());
        PendingIntent pi = PendingIntent.getBroadcast(
                this, timer.getId().hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (am != null) {
            try {
                am.setExact(AlarmManager.RTC_WAKEUP, timer.getEndTimeMillis(), pi);
            } catch (SecurityException e) {
                // 用户可能禁用了闹钟权限
            }
        }
    }

    private void cancelAlarm(TimerData timer) {
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent intent = new Intent(this, TimerReceiver.class);
        intent.putExtra(EXTRA_TIMER_ID, timer.getId());
        PendingIntent pi = PendingIntent.getBroadcast(
                this, timer.getId().hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (am != null) am.cancel(pi);
    }

    // ── 铃声播放（循环 + 手动停止 + 2分钟超时）─────────────────────

    private void triggerAlarm(TimerData timer) {
        stopAlarm(); // 停止之前的铃声
        playAlarm(timer);
        showAlarmNotification(timer);
    }

    private void playAlarm(TimerData timer) {
        try {
            Uri uri;
            if (timer.getRingtoneUri() != null && !timer.getRingtoneUri().isEmpty()) {
                uri = Uri.parse(timer.getRingtoneUri());
            } else {
                uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            }

            // 设置音量
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (am != null) {
                int maxVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                int vol = (int) (maxVol * timer.getVolume() / 100.0);
                am.setStreamVolume(AudioManager.STREAM_ALARM, vol, 0);
            }

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, uri);
            mediaPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build());
            mediaPlayer.setLooping(true); // 循环播放
            mediaPlayer.prepare();
            mediaPlayer.start();

            currentAlarmTimerId = timer.getId();

            // 2分钟后自动停止
            stopAlarmRunnable = () -> {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    stopAlarm();
                }
            };
            handler.postDelayed(stopAlarmRunnable, ALARM_DURATION_MS);

        } catch (Exception e) {
            e.printStackTrace();
            stopAlarm();
        }
    }

    private void stopAlarm() {
        if (stopAlarmRunnable != null) {
            handler.removeCallbacks(stopAlarmRunnable);
            stopAlarmRunnable = null;
        }
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        currentAlarmTimerId = null;

        // 取消闹钟通知
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null && currentAlarmTimerId != null) {
            nm.cancel(currentAlarmTimerId.hashCode());
        }
    }

    private void showAlarmNotification(TimerData timer) {
        // 停止按钮 Intent
        Intent stopIntent = new Intent(this, TimerService.class);
        stopIntent.setAction(ACTION_STOP_ALARM);
        PendingIntent stopPi = PendingIntent.getService(
                this, timer.getId().hashCode() + 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ALARM)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("⏰ 计时完成")
                .setContentText(timer.getLabel() + " - " + timer.getFormattedTotal())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .setAutoCancel(false)
                .addAction(android.R.drawable.ic_media_pause, "停止", stopPi);

        if (nm != null) {
            nm.notify(timer.getId().hashCode(), builder.build());
        }
    }

    // ── 前台通知 ─────────────────────────────────────────────────

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification());
    }

    private android.app.Notification buildNotification() {
        int runningCount = 0;
        for (TimerData t : timers) if (t.isRunning()) runningCount++;
        String content;
        if (timers.isEmpty()) {
            content = "无活跃计时器";
        } else {
            StringBuilder sb = new StringBuilder();
            for (TimerData t : timers) {
                if (t.isRunning()) sb.append(t.getLabel()).append(" ").append(t.getFormattedRemaining()).append(" | ");
            }
            if (sb.length() > 0) {
                sb.setLength(sb.length() - 3);
                content = sb.toString();
            } else {
                content = timers.size() + "个计时器已暂停";
            }
        }
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("多路计时器 (" + runningCount + "/" + timers.size() + " 运行中)")
                .setContentText(content)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannels() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel sc = new NotificationChannel(
                    CHANNEL_ID, "计时服务", NotificationManager.IMPORTANCE_LOW);
            sc.setDescription("后台计时服务通知");
            nm.createNotificationChannel(sc);

            NotificationChannel ac = new NotificationChannel(
                    CHANNEL_ALARM, "计时完成", NotificationManager.IMPORTANCE_HIGH);
            ac.setDescription("计时完成闹钟通知");
            ac.setSound(null, null);
            nm.createNotificationChannel(ac);
        }
    }

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MultiTimer:WakeLock");
            wakeLock.acquire(24 * 60 * 60 * 1000L);
        }
    }

    @Override
    public void onDestroy() {
        stopAlarm();
        handler.removeCallbacks(tickRunnable);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        instance = null;
        super.onDestroy();
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }
}
