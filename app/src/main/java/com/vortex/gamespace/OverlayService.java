package com.vortex.gamespace;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Collections;
import java.util.Locale;

public class OverlayService extends Service {
    private static final String CH = "vortex_panel";

    private WindowManager wm;
    private SharedPreferences prefs;
    private final Handler h = new Handler(Looper.getMainLooper());
    private float d;

    private FrameLayout handle;
    private View pill;
    private WindowManager.LayoutParams handleLp;
    private View button;
    private FrameLayout panel;
    private SidePanel left, right;
    private TextView cpuVal, cpuTemp, cpuBat, ramVal, ramSub, dndTile;
    private boolean edgeRight;
    private float yFrac;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateStats();
            h.postDelayed(this, 1000);
        }
    };
    private final Runnable hideButtonRun = new Runnable() {
        @Override public void run() { removeButton(); }
    };

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        d = getResources().getDisplayMetrics().density;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        prefs = getSharedPreferences("vortex", MODE_PRIVATE);
        edgeRight = prefs.getBoolean("edgeRight", true);
        yFrac = prefs.getFloat("yFrac", 0.35f);
        goForeground();
        if (Settings.canDrawOverlays(this)) createHandle();
        else stopSelf();
    }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        closePanelNow();
        removeButton();
        applyHandle();
    }

    @Override
    public void onDestroy() {
        h.removeCallbacksAndMessages(null);
        removeButton();
        closePanelNow();
        if (handle != null) {
            try { wm.removeView(handle); } catch (Exception ignored) { }
            handle = null;
        }
        super.onDestroy();
    }

    private void goForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                    new NotificationChannel(CH, "Painel de jogo", NotificationManager.IMPORTANCE_MIN));
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        b.setContentTitle("VorteX Game Space")
                .setContentText("Painel lateral ativo")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pi)
                .setOngoing(true);
        Notification n = b.build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }
    }

    private int overlayType() {
        return Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private int smallFlags() {
        return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
    }

    private int dp(float v) { return Math.round(v * d); }

    private int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private void createHandle() {
        handle = new FrameLayout(this);
        pill = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFFFF2A3A);
        g.setCornerRadius(dp(3));
        pill.setBackground(g);
        pill.setAlpha(0.8f);
        handle.addView(pill, new FrameLayout.LayoutParams(dp(4), dp(64)));

        handleLp = new WindowManager.LayoutParams(dp(36), dp(96), overlayType(),
                smallFlags(), PixelFormat.TRANSLUCENT);
        handleLp.gravity = Gravity.TOP | Gravity.START;
        applyHandleLayout();
        wm.addView(handle, handleLp);

        if (Build.VERSION.SDK_INT >= 29) {
            handle.post(new Runnable() {
                @Override public void run() {
                    if (handle != null) {
                        handle.setSystemGestureExclusionRects(Collections.singletonList(
                                new Rect(0, 0, handle.getWidth(), handle.getHeight())));
                    }
                }
            });
        }

        handle.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            boolean moving, shownBtn;
            long t0;
            final Runnable longPress = new Runnable() {
                @Override public void run() {
                    moving = true;
                    pill.setAlpha(1f);
                    pill.setScaleX(2.2f);
                }
            };

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                DisplayMetrics m = getResources().getDisplayMetrics();
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        moving = false;
                        shownBtn = false;
                        t0 = SystemClock.uptimeMillis();
                        h.postDelayed(longPress, 450);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        if (moving) {
                            handleLp.x = clamp((int) (e.getRawX() - handleLp.width / 2f), 0, m.widthPixels - handleLp.width);
                            handleLp.y = clamp((int) (e.getRawY() - handleLp.height / 2f), 0, m.heightPixels - handleLp.height);
                            wm.updateViewLayout(handle, handleLp);
                        } else if (!shownBtn) {
                            float in = edgeRight ? downX - e.getRawX() : e.getRawX() - downX;
                            float dy = Math.abs(e.getRawY() - downY);
                            if (in > dp(22)) {
                                h.removeCallbacks(longPress);
                                shownBtn = true;
                                showButton();
                            } else if (dy > dp(10) || in < -dp(10)) {
                                h.removeCallbacks(longPress);
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        h.removeCallbacks(longPress);
                        if (moving) {
                            moving = false;
                            pill.setScaleX(1f);
                            pill.setAlpha(0.8f);
                            edgeRight = e.getRawX() > m.widthPixels / 2f;
                            yFrac = (handleLp.y + handleLp.height / 2f) / m.heightPixels;
                            prefs.edit().putBoolean("edgeRight", edgeRight).putFloat("yFrac", yFrac).apply();
                            applyHandle();
                        } else if (!shownBtn && e.getActionMasked() == MotionEvent.ACTION_UP
                                && SystemClock.uptimeMillis() - t0 < 250) {
                            showButton();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void applyHandleLayout() {
        DisplayMetrics m = getResources().getDisplayMetrics();
        handleLp.x = edgeRight ? m.widthPixels - handleLp.width : 0;
        handleLp.y = clamp(Math.round(yFrac * m.heightPixels) - handleLp.height / 2,
                0, Math.max(0, m.heightPixels - handleLp.height));
        FrameLayout.LayoutParams pl = (FrameLayout.LayoutParams) pill.getLayoutParams();
        pl.gravity = (edgeRight ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL;
        pl.setMargins(edgeRight ? 0 : dp(10), 0, edgeRight ? dp(10) : 0, 0);
        pill.setLayoutParams(pl);
    }

    private void applyHandle() {
        if (handle == null) return;
        applyHandleLayout();
        try { wm.updateViewLayout(handle, handleLp); } catch (Exception ignored) { }
    }

    private void showButton() {
        h.removeCallbacks(hideButtonRun);
        if (button != null) {
            h.postDelayed(hideButtonRun, 4000);
            return;
        }
        int s = dp(58);
        FrameLayout b = new FrameLayout(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0xF0101015);
        g.setStroke(dp(2), 0xFFFF2A3A);
        b.setBackground(g);
        b.addView(new LogoView(this), new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER));
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                removeButton();
                showPanel();
            }
        });

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(s, s, overlayType(),
                smallFlags(), PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = edgeRight ? handleLp.x - s - dp(6) : handleLp.x + handleLp.width + dp(6);
        lp.y = handleLp.y + (handleLp.height - s) / 2;
        b.setAlpha(0f);
        b.setScaleX(0.6f);
        b.setScaleY(0.6f);
        wm.addView(b, lp);
        button = b;
        b.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160);
        h.postDelayed(hideButtonRun, 4000);
    }

    private void removeButton() {
        h.removeCallbacks(hideButtonRun);
        if (button != null) {
            try { wm.removeView(button); } catch (Exception ignored) { }
            button = null;
        }
    }

    private void showPanel() {
        if (panel != null) return;
        DisplayMetrics m = getResources().getDisplayMetrics();
        boolean land = m.widthPixels > m.heightPixels;
        int pw = Math.max(dp(150), Math.min(Math.round(m.widthPixels * (land ? 0.30f : 0.42f)), dp(300)));

        panel = new FrameLayout(this);
        panel.setBackgroundColor(0x22000000);
        panel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { hidePanel(); }
        });
        left = buildCpuPanel();
        right = buildRamPanel();
        panel.addView(left, new FrameLayout.LayoutParams(pw, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START));
        panel.addView(right, new FrameLayout.LayoutParams(pw, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END));

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        wm.addView(panel, lp);

        left.setTranslationX(-pw);
        right.setTranslationX(pw);
        left.animate().translationX(0).setDuration(220);
        right.animate().translationX(0).setDuration(220);

        updateStats();
        h.postDelayed(tick, 1000);
    }

    private void hidePanel() {
        if (panel == null) return;
        h.removeCallbacks(tick);
        final FrameLayout p = panel;
        final View l = left, r = right;
        panel = null;
        left = null;
        right = null;
        l.animate().translationX(-l.getWidth()).setDuration(180);
        r.animate().translationX(r.getWidth()).setDuration(180).withEndAction(new Runnable() {
            @Override public void run() {
                try { wm.removeView(p); } catch (Exception ignored) { }
            }
        });
    }

    private void closePanelNow() {
        h.removeCallbacks(tick);
        if (panel != null) {
            try { wm.removeView(panel); } catch (Exception ignored) { }
            panel = null;
            left = null;
            right = null;
        }
    }

    private SidePanel buildCpuPanel() {
        SidePanel p = new SidePanel(this, false);
        LinearLayout c = col();
        c.setGravity(Gravity.CENTER);
        c.addView(label("CPU"));
        cpuVal = big("--");
        c.addView(cpuVal);
        c.addView(small("GHz"));
        cpuTemp = small("TEMP  --");
        cpuBat = small("BATERIA  --");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(16);
        c.addView(cpuTemp, lp);
        c.addView(cpuBat);
        p.addView(c, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        p.bind(c, cpuVal);
        return p;
    }

    private SidePanel buildRamPanel() {
        SidePanel p = new SidePanel(this, true);
        LinearLayout c = col();
        c.setGravity(Gravity.CENTER);
        c.addView(label("RAM"));
        ramVal = big("--");
        c.addView(ramVal);
        c.addView(small("%"));
        ramSub = small("-- / -- GB");
        c.addView(ramSub);

        GridLayout g = new GridLayout(this);
        g.setColumnCount(2);
        dndTile = tile(g, "NÃO PERTURBE", new View.OnClickListener() {
            @Override public void onClick(View v) { toggleDnd(); }
        });
        tile(g, "TELA", new View.OnClickListener() {
            @Override public void onClick(View v) { openAndClose(Settings.ACTION_DISPLAY_SETTINGS); }
        });
        tile(g, "BATERIA", new View.OnClickListener() {
            @Override public void onClick(View v) { openAndClose(Settings.ACTION_BATTERY_SAVER_SETTINGS); }
        });
        tile(g, "FECHAR", new View.OnClickListener() {
            @Override public void onClick(View v) { hidePanel(); }
        });
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gl.topMargin = dp(14);
        c.addView(g, gl);

        p.addView(c, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        p.bind(c, ramVal);
        return p;
    }

    private void updateStats() {
        if (left == null || right == null) return;
        float[] cpu = Stats.cpu();
        float[] bat = Stats.battery(this);
        int[] ram = Stats.ram(this);

        if (cpu != null) {
            cpuVal.setText(String.format(Locale.US, "%.2f", cpu[0]));
            left.setLevel(cpu[1]);
        } else {
            cpuVal.setText("--");
            left.setLevel(0f);
        }
        cpuTemp.setText(String.format(Locale.US, "TEMP  %.0f°C", bat[1]));
        cpuBat.setText(String.format(Locale.US, "BATERIA  %.0f%%", bat[0]));

        ramVal.setText(String.valueOf(ram[0]));
        right.setLevel(ram[0] / 100f);
        ramSub.setText(String.format(Locale.US, "%.1f / %.1f GB", ram[1] / 1024f, ram[2] / 1024f));
        refreshDndTile();
    }

    private void toggleDnd() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (!nm.isNotificationPolicyAccessGranted()) {
            openAndClose(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
            return;
        }
        boolean on = nm.getCurrentInterruptionFilter() > NotificationManager.INTERRUPTION_FILTER_ALL;
        nm.setInterruptionFilter(on ? NotificationManager.INTERRUPTION_FILTER_ALL
                : NotificationManager.INTERRUPTION_FILTER_PRIORITY);
        refreshDndTile();
    }

    private void refreshDndTile() {
        if (dndTile == null) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        boolean on = nm.isNotificationPolicyAccessGranted()
                && nm.getCurrentInterruptionFilter() > NotificationManager.INTERRUPTION_FILTER_ALL;
        dndTile.setBackground(on
                ? rounded(0x55FF2A3A, 0xFFFF2A3A, dp(8))
                : rounded(0xFF1B1B23, 0x55FF2A3A, dp(8)));
    }

    private void openAndClose(String action) {
        Intent i = new Intent(action);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception ignored) { }
        hidePanel();
    }

    private LinearLayout col() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private GradientDrawable rounded(int fill, int stroke, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(r);
        g.setStroke(dp(1), stroke);
        return g;
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(0xFFFF2A3A);
        t.setTextSize(11);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.3f);
        return t;
    }

    private TextView big(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(40);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.04f);
        t.setIncludeFontPadding(false);
        return t;
    }

    private TextView small(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(0xFF8B8B97);
        t.setTextSize(12);
        return t;
    }

    private TextView tile(GridLayout g, String text, View.OnClickListener click) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(Color.WHITE);
        v.setTextSize(9);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.CENTER);
        v.setMaxLines(2);
        v.setLetterSpacing(0.05f);
        v.setBackground(rounded(0xFF1B1B23, 0x55FF2A3A, dp(8)));
        v.setOnClickListener(click);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f));
        lp.width = 0;
        lp.height = dp(40);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        g.addView(v, lp);
        return v;
    }
          }
