package com.example.multitimer;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * 计时器数据模型
 */
public class TimerData implements Parcelable {
    private String id;          // 唯一标识
    private String label;       // 标签
    private long totalSeconds;  // 总秒数
    private long remainingSeconds; // 剩余秒数
    private boolean running;    // 是否运行中
    private long endTimeMillis; // 结束时间戳 (System.currentTimeMillis + remaining*1000)
    private String ringtoneUri; // 铃声URI (null表示默认)
    private int volume;         // 铃声音量 0-100

    public TimerData(String id, String label, long totalSeconds) {
        this.id = id;
        this.label = label;
        this.totalSeconds = totalSeconds;
        this.remainingSeconds = totalSeconds;
        this.running = false;
        this.endTimeMillis = 0;
        this.ringtoneUri = null;
        this.volume = 80;
    }

    // Parcelable 实现
    protected TimerData(Parcel in) {
        id = in.readString();
        label = in.readString();
        totalSeconds = in.readLong();
        remainingSeconds = in.readLong();
        running = in.readByte() != 0;
        endTimeMillis = in.readLong();
        ringtoneUri = in.readString();
        volume = in.readInt();
    }

    public static final Creator<TimerData> CREATOR = new Creator<TimerData>() {
        @Override
        public TimerData createFromParcel(Parcel in) { return new TimerData(in); }
        @Override
        public TimerData[] newArray(int size) { return new TimerData[size]; }
    };

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(label);
        dest.writeLong(totalSeconds);
        dest.writeLong(remainingSeconds);
        dest.writeByte((byte) (running ? 1 : 0));
        dest.writeLong(endTimeMillis);
        dest.writeString(ringtoneUri);
        dest.writeInt(volume);
    }

    // Getter/Setter
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public long getTotalSeconds() { return totalSeconds; }
    public void setTotalSeconds(long totalSeconds) { this.totalSeconds = totalSeconds; }
    public long getRemainingSeconds() { return remainingSeconds; }
    public void setRemainingSeconds(long remainingSeconds) { this.remainingSeconds = remainingSeconds; }
    public boolean isRunning() { return running; }
    public void setRunning(boolean running) { this.running = running; }
    public long getEndTimeMillis() { return endTimeMillis; }
    public void setEndTimeMillis(long endTimeMillis) { this.endTimeMillis = endTimeMillis; }
    public String getRingtoneUri() { return ringtoneUri; }
    public void setRingtoneUri(String ringtoneUri) { this.ringtoneUri = ringtoneUri; }
    public int getVolume() { return volume; }
    public void setVolume(int volume) { this.volume = volume; }

    /** 更新剩余时间（基于结束时间戳） */
    public void updateRemaining() {
        if (running && endTimeMillis > 0) {
            long now = System.currentTimeMillis();
            remainingSeconds = Math.max(0, (endTimeMillis - now) / 1000);
        }
    }

    /** 获取格式化的时间字符串 */
    public String getFormattedRemaining() {
        long hrs = remainingSeconds / 3600;
        long mins = (remainingSeconds % 3600) / 60;
        long secs = remainingSeconds % 60;
        if (hrs > 0) {
            return String.format("%d:%02d:%02d", hrs, mins, secs);
        }
        return String.format("%02d:%02d", mins, secs);
    }

    /** 获取格式化的总时间字符串 */
    public String getFormattedTotal() {
        long hrs = totalSeconds / 3600;
        long mins = (totalSeconds % 3600) / 60;
        long secs = totalSeconds % 60;
        if (hrs > 0) {
            return String.format("%dh %dm %ds", hrs, mins, secs);
        }
        if (mins > 0) {
            return String.format("%dm %ds", mins, secs);
        }
        return String.format("%ds", secs);
    }
}
