package com.example.multitimer;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * 主界面 - 多路计时器
 */
public class MainActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private TimerAdapter adapter;
    private FloatingActionButton addBtn;
    private Button presetBtn;
    private ActivityResultLauncher<String[]> permissionLauncher;
    private ActivityResultLauncher<Intent> ringtoneLauncher;
    private String pendingRingtoneTimerId;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pollRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recycler_view);
        addBtn = findViewById(R.id.btn_add);
        presetBtn = findViewById(R.id.btn_presets);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TimerAdapter(this, new ArrayList<>(), this::refreshTimerList);
        recyclerView.setAdapter(adapter);

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean allGranted = !result.containsValue(false);
                    if (allGranted) {
                        startTimerService();
                    } else {
                        Toast.makeText(this, "需要通知权限才能后台运行计时器", Toast.LENGTH_LONG).show();
                    }
                });

        ringtoneLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getParcelableExtra(
                                RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                        if (uri != null && pendingRingtoneTimerId != null) {
                            Intent si = new Intent(this, TimerService.class);
                            si.setAction(TimerService.ACTION_RINGTONE);
                            si.putExtra(TimerService.EXTRA_TIMER_ID, pendingRingtoneTimerId);
                            si.putExtra(TimerService.EXTRA_RINGTONE, uri.toString());
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(si);
                            } else {
                                startService(si);
                            }
                            Toast.makeText(this, "铃声已设置", Toast.LENGTH_SHORT).show();
                        }
                    }
                    pendingRingtoneTimerId = null;
                });

        addBtn.setOnClickListener(v -> showAddTimerDialog());
        presetBtn.setOnClickListener(v -> showPresetsDialog());

        checkPermissions();
        checkMiuiOptimization();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startPolling();
        refreshTimerList();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPolling();
    }

    /** 启动轮询刷新（替代广播） */
    private void startPolling() {
        if (pollRunnable != null) return;
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                refreshTimerList();
                handler.postDelayed(this, 500); // 每500ms刷新
            }
        };
        handler.post(pollRunnable);
    }

    private void stopPolling() {
        if (pollRunnable != null) {
            handler.removeCallbacks(pollRunnable);
            pollRunnable = null;
        }
    }

    private void checkPermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        if (!permissions.isEmpty()) {
            permissionLauncher.launch(permissions.toArray(new String[0]));
        } else {
            startTimerService();
        }
    }

    private void startTimerService() {
        Intent intent = new Intent(this, TimerService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    /** 发送指令到 Service 并延迟刷新 UI */
    private void sendServiceAction(String action, String timerId) {
        Intent intent = new Intent(this, TimerService.class);
        intent.setAction(action);
        if (timerId != null) {
            intent.putExtra(TimerService.EXTRA_TIMER_ID, timerId);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        // 延迟刷新确保 Service 处理完毕
        handler.postDelayed(this::refreshTimerList, 150);
    }

    private void sendServiceAction(String action, String timerId, String label, long seconds) {
        Intent intent = new Intent(this, TimerService.class);
        intent.setAction(action);
        if (timerId != null) intent.putExtra(TimerService.EXTRA_TIMER_ID, timerId);
        if (label != null) intent.putExtra(TimerService.EXTRA_LABEL, label);
        intent.putExtra(TimerService.EXTRA_SECONDS, seconds);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        handler.postDelayed(this::refreshTimerList, 150);
    }

    /**
     * 小米澎湃(HyperOS) / MIUI 特殊适配
     */
    private void checkMiuiOptimization() {
        if (isMiui()) {
            try {
                String pkg = getPackageName();
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + pkg));
                if (intent.resolveActivity(getPackageManager()) != null) {
                    new AlertDialog.Builder(this)
                            .setTitle("小米澎湃系统优化")
                            .setMessage("为确保计时器在后台稳定运行，建议关闭电池优化和开启自启动权限。\n\n" +
                                    "请在系统设置中：\n" +
                                    "1. 关闭电池优化\n" +
                                    "2. 开启「自启动」\n" +
                                    "3. 锁定应用后台")
                            .setPositiveButton("去设置", (d, w) -> startActivity(intent))
                            .setNegativeButton("稍后", null)
                            .show();
                }
            } catch (Exception e) { /* 忽略 */ }
        }
    }

    private boolean isMiui() {
        String manufacturer = Build.MANUFACTURER;
        return manufacturer != null && manufacturer.toLowerCase().contains("xiaomi");
    }

    /**
     * 显示添加计时器对话框
     */
    private void showAddTimerDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_timer, null);
        EditText labelInput = dialogView.findViewById(R.id.input_label);
        NumberPicker hourPicker = dialogView.findViewById(R.id.picker_hour);
        NumberPicker minPicker = dialogView.findViewById(R.id.picker_minute);
        NumberPicker secPicker = dialogView.findViewById(R.id.picker_second);

        hourPicker.setMinValue(0); hourPicker.setMaxValue(99);
        minPicker.setMinValue(0); minPicker.setMaxValue(59);
        secPicker.setMinValue(0); secPicker.setMaxValue(59);

        new AlertDialog.Builder(this)
                .setTitle("添加计时器")
                .setView(dialogView)
                .setPositiveButton("添加", (d, w) -> {
                    String label = labelInput.getText().toString().trim();
                    if (label.isEmpty()) label = "计时器";
                    long totalSec = hourPicker.getValue() * 3600L
                            + minPicker.getValue() * 60L
                            + secPicker.getValue();
                    if (totalSec == 0) {
                        Toast.makeText(this, "时间不能为0", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    sendServiceAction(TimerService.ACTION_ADD, null, label, totalSec);
                    PresetManager.getInstance(this).addPreset(label, totalSec);
                    Toast.makeText(this, "已添加: " + label, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 显示收藏预设对话框
     */
    private void showPresetsDialog() {
        final List<PresetManager.Preset> presets;
        List<PresetManager.Preset> loaded = PresetManager.getInstance(this).getPresets();
        if (loaded.isEmpty()) {
            loaded = PresetManager.getDefaultPresets();
            for (PresetManager.Preset p : loaded) {
                PresetManager.getInstance(this).addPreset(p.label, p.seconds);
            }
        }
        presets = loaded;

        String[] items = new String[presets.size()];
        for (int i = 0; i < presets.size(); i++) {
            PresetManager.Preset p = presets.get(i);
            long hrs = p.seconds / 3600;
            long mins = (p.seconds % 3600) / 60;
            long secs = p.seconds % 60;
            if (hrs > 0) items[i] = p.label + "  (" + hrs + "h" + mins + "m" + secs + "s)";
            else if (mins > 0) items[i] = p.label + "  (" + mins + "m" + secs + "s)";
            else items[i] = p.label + "  (" + secs + "s)";
        }

        new AlertDialog.Builder(this)
                .setTitle("常用计时")
                .setItems(items, (d, which) -> {
                    PresetManager.Preset p = presets.get(which);
                    sendServiceAction(TimerService.ACTION_ADD, null, p.label, p.seconds);
                    Toast.makeText(this, "已添加: " + p.label, Toast.LENGTH_SHORT).show();
                })
                .setPositiveButton("管理收藏", (d, w) -> showPresetManageDialog(presets))
                .setNegativeButton("关闭", null)
                .show();
    }

    private void showPresetManageDialog(List<PresetManager.Preset> presets) {
        String[] items = new String[presets.size()];
        for (int i = 0; i < presets.size(); i++) {
            items[i] = presets.get(i).label + " (" + presets.get(i).seconds + "s)";
        }
        new AlertDialog.Builder(this)
                .setTitle("管理收藏 (点击删除)")
                .setItems(items, (d, which) -> {
                    PresetManager.getInstance(this).removePreset(which);
                    Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("返回", null)
                .show();
    }

    /** 系统铃声选择器（由 TimerAdapter 调用） */
    public void startRingtonePicker(String timerId, Intent intent) {
        pendingRingtoneTimerId = timerId;
        ringtoneLauncher.launch(intent);
    }

    /** 刷新计时器列表 */
    private void refreshTimerList() {
        TimerService service = TimerService.getInstance();
        if (service != null) {
            List<TimerData> currentTimers = service.getTimers();
            adapter.updateData(new ArrayList<>(currentTimers));
            if (currentTimers.isEmpty()) {
                findViewById(R.id.empty_hint).setVisibility(View.VISIBLE);
            } else {
                findViewById(R.id.empty_hint).setVisibility(View.GONE);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPolling();
    }
}
