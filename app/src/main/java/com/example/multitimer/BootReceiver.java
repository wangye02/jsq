package com.example.multitimer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 开机自启接收器 - 适配小米澎湃系统
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // 开机后启动服务（如果之前有运行中的计时器）
            // 注意：小米系统可能需要用户手动授权自启动
            Intent serviceIntent = new Intent(context, TimerService.class);
            context.startService(serviceIntent);
        }
    }
}
