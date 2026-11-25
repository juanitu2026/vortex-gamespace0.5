package com.vortex.gamespace;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    static final int BG = 0xFF0A0A0D, CARD = 0xFF15151B, RED = 0xFFFF2A3A, MUTED = 0xFF8B8B97;

    static class Game {
        String pkg, label;
        Drawable icon;
        boolean isGame;
    }

    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean homeBuilt, waitingOverlay, syncing;

    private GridLayout grid;
    private ProgressBar pbRam, pbTemp, pbBat;
    private TextView tvRam, tvTemp, tvBat;
    private Switch swDnd, swPanel;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateStats();
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("vortex", MODE_PRIVATE);
        Window w = getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);
        showSplash();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!homeBuilt) return;
        restoreDnd();
        syncPanel();
        refreshGames();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    private void showSplash() {
        FrameLayout f = new FrameLayout(this);
        f.setBackgroundColor(BG);
        LinearLayout c = col();
        c.setGravity(Gravity.CENTER);
        c.addView(new LogoView(this), new LinearLayout.LayoutParams(dp(96), dp(96)));
        TextView t = text("VORTEX", 20, Color.WHITE, true);
        t.setLetterSpacing(0.5f);
        c.addView(t);
        f.addView(c, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        c.setAlpha(0f);
        c.setScaleX(0.85f);
        c.setScaleY(0.85f);
        setContentView(f);
        c.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(500).withEndAction(new Runnable() {
            @Override public void run() {
                handler.postDelayed(new Runnable() {
                    @Override public void run() { buildHome(); }
                }, 500);
            }
        });
    }

    private void buildHome() {
        homeBuilt = true;
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        LinearLayout root = col();
        root.setPadding(dp(18), dp(26), dp(18), dp(32));
        sv.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout head = row();
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(new LogoView(this), new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout titles = col();
        titles.setPadding(dp(12), 0, 0, 0);
        TextView a = text("GAME SPACE", 22, Color.WHITE, true);
        a.setLetterSpacing(0.1f);
        TextView b = text("VORTEX", 11, RED, true);
        b.setLetterSpacing(0.4f);
        titles.addView(a);
        titles.addView(b);
        head.addView(titles);
        root.addView(head);

        LinearLayout sec = row();
        sec.setGravity(Gravity.CENTER_VERTICAL);
        TextView st = text("MEUS JOGOS", 12, MUTED, true);
        st.setLetterSpacing(0.2f);
        sec.addView(st, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView edit = text("Editar lista", 13, RED, true);
        edit.setPadding(dp(8), dp(8), 0, dp(8));
        edit.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { editList(); }
        });
        sec.addView(edit);
        LinearLayout.LayoutParams secLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        secLp.topMargin = dp(26);
        root.addView(sec, secLp);

        grid = new GridLayout(this);
        root.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView pt = text("PAINEL DE JOGO", 12, MUTED, true);
        pt.setLetterSpacing(0.2f);
        LinearLayout.LayoutParams ptLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ptLp.topMargin = dp(26);
        ptLp.bottomMargin = dp(8);
        root.addView(pt, ptLp);

        LinearLayout stats = col();
        stats.setBackground(round(CARD, dp(16)));
        stats.setPadding(dp(16), dp(10), dp(16), dp(10));
        pbRam = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pbTemp = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pbBat = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        tvRam = text("--", 13, Color.WHITE, true);
        tvTemp = text("--", 13, Color.WHITE, true);
        tvBat = text("--", 13, Color.WHITE, true);
        stats.addView(statRow("RAM", pbRam, tvRam));
        stats.addView(statRow("TEMP", pbTemp, tvTemp));
        stats.addView(statRow("BATERIA", pbBat, tvBat));
        root.addView(stats, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        swDnd = makeSwitch();
        swDnd.setChecked(prefs.getBoolean("dndOn", false));
        swDnd.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean on) {
                prefs.edit().putBoolean("dndOn", on).apply();
                if (on && !dndGranted()) {
                    toast("Permita o acesso ao Não perturbe para o VorteX");
                    openSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                }
            }
        });
        addSetting(root, "Não perturbe no jogo", "Silencia avisos enquanto você joga", swDnd, null);

        addSetting(root, "Economia de bateria", "Abrir ajustes de bateria", arrow(),
                new View.OnClickListener() {
                    @Override public void onClick(View v) { openSettings(Settings.ACTION_BATTERY_SAVER_SETTINGS); }
                });
        addSetting(root, "Tela e brilho", "Abrir ajustes da tela", arrow(),
                new View.OnClickListener() {
                    @Override public void onClick(View v) { openSettings(Settings.ACTION_DISPLAY_SETTINGS); }
                });

        swPanel = makeSwitch();
        swPanel.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean on) {
                if (!syncing) setPanel(on);
            }
        });
        addSetting(root, "Painel lateral", "Linha escondida na borda com CPU e RAM por cima do jogo", swPanel, null);

        TextView tip = text("Como usar: arraste a linha vermelha da borda para dentro da tela e aperte o botão que aparece. "
                + "Para mudar a linha de lugar, segure nela e arraste.", 12, MUTED, false);
        tip.setPadding(dp(4), dp(2), dp(4), 0);
        root.addView(tip);

        setContentView(sv);
        sv.setAlpha(0f);
        sv.animate().alpha(1f).setDuration(300);

        refreshGames();
        syncPanel();
        updateStats();
        handler.post(tick);
    }

    private View statRow(String name, ProgressBar pb, TextView value) {
        LinearLayout r = row();
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(8), 0, dp(8));
        TextView n = text(name, 12, MUTED, true);
        n.setLetterSpacing(0.1f);
        r.addView(n, new LinearLayout.LayoutParams(dp(70), ViewGroup.LayoutParams.WRAP_CONTENT));
        pb.setMax(100);
        pb.setProgressTintList(ColorStateList.valueOf(RED));
        pb.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF2A2A33));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(0, dp(8), 1f);
        pl.leftMargin = dp(6);
        pl.rightMargin = dp(12);
        r.addView(pb, pl);
        r.addView(value, new LinearLayout.LayoutParams(dp(62), ViewGroup.LayoutParams.WRAP_CONTENT));
        value.setGravity(Gravity.END);
        return r;
    }

    private void updateStats() {
        if (pbRam == null) return;
        int[] r = Stats.ram(this);
        float[] b = Stats.battery(this);
        pbRam.setProgress(r[0]);
        tvRam.setText(r[0] + "%");
        float t = Math.max(0f, Math.min(1f, (b[1] - 20f) / 40f));
        pbTemp.setProgress(Math.round(t * 100));
        tvTemp.setText(String.format(Locale.US, "%.1f°C", b[1]));
        pbBat.setProgress(Math.round(b[0]));
        tvBat.setText(String.format(Locale.US, "%.0f%%", b[0]));
    }

    private List<Game> allApps() {
        PackageManager pm = getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        List<Game> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
            Game g = new Game();
            g.pkg = pkg;
            g.label = String.valueOf(ri.loadLabel(pm));
            g.icon = ri.loadIcon(pm);
            g.isGame = Build.VERSION.SDK_INT >= 26
                    && ri.activityInfo.applicationInfo.category == ApplicationInfo.CATEGORY_GAME;
            out.add(g);
        }
        Collections.sort(out, (x, y) -> x.label.compareToIgnoreCase(y.label));
        return out;
    }

    private void refreshGames() {
        if (grid == null) return;
        List<Game> all = allApps();
        Set<String> saved = prefs.getStringSet("games", null);
        if (saved == null) {
            saved = new HashSet<>();
            for (Game g : all) if (g.isGame) saved.add(g.pkg);
            prefs.edit().putStringSet("games", saved).apply();
            if (saved.isEmpty()) {
                handler.post(new Runnable() {
                    @Override public void run() { editList(); }
                });
            }
        }
        grid.removeAllViews();
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int cols = Math.max(4, (screenW - dp(36)) / dp(86));
        int tileW = (screenW - dp(36)) / cols;
        grid.setColumnCount(cols);

        int count = 0;
        for (final Game g : all) {
            if (!saved.contains(g.pkg)) continue;
            count++;
            LinearLayout t = col();
            t.setGravity(Gravity.CENTER_HORIZONTAL);
            t.setPadding(dp(4), dp(12), dp(4), dp(6));
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(g.icon);
            t.addView(iv, new LinearLayout.LayoutParams(dp(54), dp(54)));
            TextView l = text(g.label, 11, Color.WHITE, false);
            l.setSingleLine(true);
            l.setEllipsize(TextUtils.TruncateAt.END);
            l.setGravity(Gravity.CENTER);
            l.setPadding(0, dp(6), 0, 0);
            t.addView(l, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { launchGame(g); }
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = tileW;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            grid.addView(t, lp);
        }
        if (count == 0) {
            TextView e = text("Escolha seus jogos", 15, RED, true);
            e.setPadding(0, dp(18), 0, dp(18));
            e.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { editList(); }
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.columnSpec = GridLayout.spec(0, cols);
            grid.addView(e, lp);
        }
    }

    private void editList() {
        final List<Game> all = allApps();
        Set<String> saved = prefs.getStringSet("games", new HashSet<String>());
        String[] names = new String[all.size()];
        final boolean[] checked = new boolean[all.size()];
        for (int i = 0; i < all.size(); i++) {
            names[i] = all.get(i).label;
            checked[i] = saved.contains(all.get(i).pkg);
        }
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Escolha seus jogos")
                .setMultiChoiceItems(names, checked, new android.content.DialogInterface.OnMultiChoiceClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int which, boolean isChecked) {
                        checked[which] = isChecked;
                    }
                })
                .setPositiveButton("Salvar", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int which) {
                        Set<String> s = new HashSet<>();
                        for (int i = 0; i < all.size(); i++) if (checked[i]) s.add(all.get(i).pkg);
                        prefs.edit().putStringSet("games", s).apply();
                        refreshGames();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void launchGame(Game g) {
        Intent li = getPackageManager().getLaunchIntentForPackage(g.pkg);
        if (li == null) {
            toast("Não consegui abrir " + g.label);
            return;
        }
        if (swDnd != null && swDnd.isChecked() && !enableDnd()) return;
        toast("ABRINDO " + g.label.toUpperCase());
        try {
            startActivity(li);
        } catch (Exception e) {
            toast("Não foi possível abrir");
        }
    }

    private boolean dndGranted() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        return nm.isNotificationPolicyAccessGranted();
    }

    private boolean enableDnd() {
        if (!dndGranted()) {
            toast("Permita o acesso ao Não perturbe para o VorteX");
            openSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
            return false;
        }
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (!prefs.getBoolean("dndActive", false)) {
            prefs.edit().putInt("prevFilter", nm.getCurrentInterruptionFilter())
                    .putBoolean("dndActive", true).apply();
        }
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
        return true;
    }

    private void restoreDnd() {
        if (!prefs.getBoolean("dndActive", false)) return;
        prefs.edit().putBoolean("dndActive", false).apply();
        if (!dndGranted()) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        int prev = prefs.getInt("prevFilter", NotificationManager.INTERRUPTION_FILTER_ALL);
        if (prev < NotificationManager.INTERRUPTION_FILTER_ALL) prev = NotificationManager.INTERRUPTION_FILTER_ALL;
        nm.setInterruptionFilter(prev);
    }

    private void setPanel(boolean on) {
        if (on) {
            if (!Settings.canDrawOverlays(this)) {
                waitingOverlay = true;
                toast("Permita \"Sobrepor a outros apps\" para o VorteX");
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
                return;
            }
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
            }
            prefs.edit().putBoolean("panel", true).apply();
            startPanelService();
        } else {
            prefs.edit().putBoolean("panel", false).apply();
            stopService(new Intent(this, OverlayService.class));
        }
    }

    private void syncPanel() {
        if (swPanel == null) return;
        boolean can = Settings.canDrawOverlays(this);
        if (waitingOverlay) {
            waitingOverlay = false;
            if (can) prefs.edit().putBoolean("panel", true).apply();
        }
        boolean on = prefs.getBoolean("panel", false) && can;
        syncing = true;
        swPanel.setChecked(on);
        syncing = false;
        if (on) startPanelService();
    }

    private void startPanelService() {
        Intent i = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
    }

    private void openSettings(String action) {
        try {
            startActivity(new Intent(action));
        } catch (Exception e) {
            toast("Não foi possível abrir");
        }
    }

    private void addSetting(LinearLayout root, String title, String sub, View rightView,
                            View.OnClickListener click) {
        LinearLayout card = row();
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(round(CARD, dp(16)));
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout tx = col();
        tx.addView(text(title, 15, Color.WHITE, true));
        TextView s = text(sub, 12, MUTED, false);
        s.setPadding(0, dp(2), 0, 0);
        tx.addView(s);
        card.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(rightView);
        if (click != null) card.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        root.addView(card, lp);
    }

    private Switch makeSwitch() {
        Switch sw = new Switch(this);
        int[][] states = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(states, new int[]{RED, MUTED}));
        sw.setTrackTintList(new ColorStateList(states, new int[]{0x66FF2A3A, 0x33FFFFFF}));
        return sw;
    }

    private TextView arrow() {
        return text("›", 24, MUTED, false);
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout col() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private GradientDrawable round(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(r);
        return g;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
                                }
