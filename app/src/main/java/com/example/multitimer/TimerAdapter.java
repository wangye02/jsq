package com.example.multitimer;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * 计时器列表适配器
 */
public class TimerAdapter extends RecyclerView.Adapter<TimerAdapter.ViewHolder> {
    private final Context context;
    private final List<TimerData> timers;
    private final Runnable onUpdate;

    public TimerAdapter(Context context, List<TimerData> timers, Runnable onUpdate) {
        this.context = context;
        this.timers = timers != null ? timers : new ArrayList<>();
        this.onUpdate = onUpdate;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_timer, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TimerData timer = timers.get(position);
        holder.bind(timer);
    }

    @Override
    public int getItemCount() { return timers.size(); }

    public void updateData(List<TimerData> newData) {
        timers.clear();
        if (newData != null) timers.addAll(newData);
        notifyDataSetChanged();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView labelText;
        private final TextView timeText;
        private final TextView totalText;
        private final ImageButton startPauseBtn;
        private final ImageButton resetBtn;
        private final ImageButton deleteBtn;
        private final ImageButton ringtoneBtn;
        private final SeekBar volumeBar;

        ViewHolder(View itemView) {
            super(itemView);
            labelText = itemView.findViewById(R.id.timer_label);
            timeText = itemView.findViewById(R.id.timer_time);
            totalText = itemView.findViewById(R.id.timer_total);
            startPauseBtn = itemView.findViewById(R.id.btn_start_pause);
            resetBtn = itemView.findViewById(R.id.btn_reset);
            deleteBtn = itemView.findViewById(R.id.btn_delete);
            ringtoneBtn = itemView.findViewById(R.id.btn_ringtone);
            volumeBar = itemView.findViewById(R.id.volume_bar);
        }

        void bind(TimerData timer) {
            labelText.setText(timer.getLabel());
            totalText.setText("总时长: " + timer.getFormattedTotal());

            if (timer.isRunning()) {
                timeText.setText(timer.getFormattedRemaining());
                startPauseBtn.setImageResource(android.R.drawable.ic_media_pause);
            } else {
                if (timer.getRemainingSeconds() > 0 && timer.getRemainingSeconds() < timer.getTotalSeconds()) {
                    timeText.setText(timer.getFormattedRemaining() + " (暂停)");
                } else {
                    timeText.setText(timer.getFormattedTotal());
                }
                startPauseBtn.setImageResource(android.R.drawable.ic_media_play);
            }

            // 如果剩余时间为0，显示完成状态
            if (!timer.isRunning() && timer.getRemainingSeconds() == 0 && timer.getTotalSeconds() > 0) {
                timeText.setText("完成!");
                startPauseBtn.setImageResource(android.R.drawable.ic_media_play);
            }

            volumeBar.setProgress(timer.getVolume());

            startPauseBtn.setOnClickListener(v -> {
                if (timer.isRunning()) {
                    sendAction(TimerService.ACTION_PAUSE, timer);
                } else if (timer.getRemainingSeconds() == 0 && timer.getTotalSeconds() > 0) {
                    // 已完成，重置后启动
                    sendAction(TimerService.ACTION_RESET, timer);
                    new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(() -> sendAction(TimerService.ACTION_START, timer), 100);
                } else {
                    sendAction(TimerService.ACTION_START, timer);
                }
            });

            resetBtn.setOnClickListener(v -> {
                sendAction(TimerService.ACTION_RESET, timer);
            });

            deleteBtn.setOnClickListener(v -> {
                sendAction(TimerService.ACTION_DELETE, timer);
            });

            ringtoneBtn.setOnClickListener(v -> {
                // 打开系统铃声选择器
                Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择完成铃声");
                if (timer.getRingtoneUri() != null && !timer.getRingtoneUri().isEmpty()) {
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                            Uri.parse(timer.getRingtoneUri()));
                }
                ((MainActivity) context).startRingtonePicker(timer.getId(), intent);
            });

            volumeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        Intent intent = new Intent(context, TimerService.class);
                        intent.setAction(TimerService.ACTION_VOLUME);
                        intent.putExtra(TimerService.EXTRA_TIMER_ID, timer.getId());
                        intent.putExtra(TimerService.EXTRA_VOLUME, progress);
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(intent);
                        } else {
                            context.startService(intent);
                        }
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });
        }

        private void sendAction(String action, TimerData timer) {
            Intent intent = new Intent(context, TimerService.class);
            intent.setAction(action);
            intent.putExtra(TimerService.EXTRA_TIMER_ID, timer.getId());
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        }
    }
}
