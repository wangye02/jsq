package com.example.multitimer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 计时闹钟广播接收器 - 处理AlarmManager触发
 */
public class TimerReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // 闹钟触发，通知Service处理
        Intent serviceIntent = new Intent(context, TimerService.class);
        serviceIntent.setAction(TimerService.ACTION_TICK);
        context.startService(serviceIntent);
    }
}
