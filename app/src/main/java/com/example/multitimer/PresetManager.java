package com.example.multitimer;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * 收藏预设管理器 - 管理常用计时时长
 */
public class PresetManager {
    private static final String PREFS_NAME = "presets";
    private static final String KEY_PRESETS = "preset_list";
    private static PresetManager instance;
    private final SharedPreferences prefs;
    private List<Preset> presets;

    public static class Preset {
        public String label;
        public long seconds;

        public Preset(String label, long seconds) {
            this.label = label;
            this.seconds = seconds;
        }
    }

    private PresetManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadPresets();
    }

    public static synchronized PresetManager getInstance(Context context) {
        if (instance == null) {
            instance = new PresetManager(context.getApplicationContext());
        }
        return instance;
    }

    private void loadPresets() {
        presets = new ArrayList<>();
        String json = prefs.getString(KEY_PRESETS, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                presets.add(new Preset(obj.getString("l"), obj.getLong("s")));
            }
        } catch (JSONException e) {
            presets = new ArrayList<>();
        }
    }

    private void savePresets() {
        JSONArray arr = new JSONArray();
        for (Preset p : presets) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("l", p.label);
                obj.put("s", p.seconds);
                arr.put(obj);
            } catch (JSONException ignored) {}
        }
        prefs.edit().putString(KEY_PRESETS, arr.toString()).apply();
    }

    public List<Preset> getPresets() { return new ArrayList<>(presets); }

    public void addPreset(String label, long seconds) {
        for (Preset p : presets) {
            if (p.seconds == seconds) return;
        }
        presets.add(new Preset(label, seconds));
        savePresets();
    }

    public void removePreset(int index) {
        if (index >= 0 && index < presets.size()) {
            presets.remove(index);
            savePresets();
        }
    }

    public static List<Preset> getDefaultPresets() {
        List<Preset> defaults = new ArrayList<>();
        defaults.add(new Preset("30秒", 30));
        defaults.add(new Preset("1分钟", 60));
        defaults.add(new Preset("3分钟", 180));
        defaults.add(new Preset("5分钟", 300));
        defaults.add(new Preset("10分钟", 600));
        defaults.add(new Preset("15分钟", 900));
        defaults.add(new Preset("30分钟", 1800));
        defaults.add(new Preset("1小时", 3600));
        return defaults;
    }
}
