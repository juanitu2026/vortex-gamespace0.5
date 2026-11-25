package com.vortex.gamespace;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import java.io.BufferedReader;
import java.io.FileReader;

final class Stats {
    private Stats() {}

    static int[] ram(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long total = mi.totalMem;
        long used = total - mi.availMem;
        int pct = total > 0 ? Math.round(100f * used / total) : 0;
        return new int[]{pct, (int) (used / 1048576L), (int) (total / 1048576L)};
    }

    static float[] battery(Context c) {
        Intent i = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (i == null) return new float[]{0f, 0f};
        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        return new float[]{scale > 0 ? 100f * level / scale : 0f, temp / 10f};
    }

    static float[] cpu() {
        long cur = 0, max = 0;
        for (int i = 0; i < 16; i++) {
            String base = "/sys/devices/system/cpu/cpu" + i + "/cpufreq/";
            long c = readLong(base + "scaling_cur_freq");
            long m = readLong(base + "cpuinfo_max_freq");
            if (c > cur) cur = c;
            if (m > max) max = m;
        }
        if (cur <= 0) return null;
        float level = max > 0 ? Math.min(1f, (float) cur / max) : 0.5f;
        return new float[]{cur / 1000000f, level};
    }

    private static long readLong(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            String s = r.readLine();
            return s == null ? -1 : Long.parseLong(s.trim());
        } catch (Exception e) {
            return -1;
        }
    }
}
