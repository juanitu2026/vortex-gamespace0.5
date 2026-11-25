package com.vortex.gamespace;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.widget.FrameLayout;

public class SidePanel extends FrameLayout {
    static final int RED = 0xFFFF2A3A, RED_DARK = 0xFF6E0D18, OFF = 0xFF24242C;
    private static final int SEG = 16;

    private final boolean right;
    private final float d, tip;
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint seg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path edgePath = new Path();
    private float shown;
    private ValueAnimator anim;
    private View content, anchor;

    public SidePanel(Context c, boolean right) {
        super(c);
        this.right = right;
        d = c.getResources().getDisplayMetrics().density;
        tip = 30 * d;
        setWillNotDraw(false);
        setClickable(true);

        seg.setStyle(Paint.Style.STROKE);
        seg.setStrokeCap(Paint.Cap.BUTT);
        seg.setStrokeWidth(7 * d);

        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(2 * d);
        edge.setColor(0xCCFF2A3A);

        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(d);
        ring.setColor(0x66FF2A3A);
        ring.setPathEffect(new DashPathEffect(new float[]{4 * d, 5 * d}, 0f));

        int outer = Math.round(14 * d);
        int inner = Math.round(tip + 34 * d);
        int v = Math.round(14 * d);
        setPadding(right ? inner : outer, v, right ? outer : inner, v);
    }

    void bind(View content, View anchor) {
        this.content = content;
        this.anchor = anchor;
    }

    void setLevel(float target) {
        target = Math.max(0f, Math.min(1f, target));
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(shown, target);
        anim.setDuration(450);
        anim.addUpdateListener(a -> {
            shown = (Float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        bg.setShader(new LinearGradient(0, 0, w, 0, 0xF2090A0E, 0xE6181820, Shader.TileMode.CLAMP));
    }

    private float edgeX(float y, int w, int h) {
        float k = 1f - Math.abs(2f * y / h - 1f);
        return (w - tip) + tip * k;
    }

    private static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int ar = a >>> 24, rr = (a >> 16) & 255, gr = (a >> 8) & 255, br = a & 255;
        int a2 = b >>> 24, r2 = (b >> 16) & 255, g2 = (b >> 8) & 255, b2 = b & 255;
        return ((int) (ar + (a2 - ar) * t) << 24)
                | ((int) (rr + (r2 - rr) * t) << 16)
                | ((int) (gr + (g2 - gr) * t) << 8)
                | (int) (br + (b2 - br) * t);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        c.save();
        if (right) c.scale(-1f, 1f, w / 2f, 0f);

        path.reset();
        path.moveTo(0, 0);
        path.lineTo(w - tip, 0);
        path.lineTo(w, h / 2f);
        path.lineTo(w - tip, h);
        path.lineTo(0, h);
        path.close();
        c.drawPath(path, bg);

        edgePath.reset();
        edgePath.moveTo(w - tip, 0);
        edgePath.lineTo(w, h / 2f);
        edgePath.lineTo(w - tip, h);
        c.drawPath(edgePath, edge);

        float top = 18 * d, bot = h - 18 * d;
        float step = (bot - top) / SEG, gap = 3 * d;
        float v = shown * SEG;
        for (int k = 0; k < SEG; k++) {
            float y1 = bot - k * step - gap / 2f;
            float y0 = y1 - step + gap;
            int lit = lerp(RED_DARK, RED, k / (SEG - 1f));
            seg.setColor(lerp(OFF, lit, v - k));
            c.drawLine(edgeX(y0, w, h) - 12 * d, y0, edgeX(y1, w, h) - 12 * d, y1, seg);
        }
        c.restore();

        if (content != null && anchor != null) {
            float cx = content.getLeft() + anchor.getLeft() + anchor.getWidth() / 2f;
            float cy = content.getTop() + anchor.getTop() + anchor.getHeight() / 2f;
            glow.setShader(new RadialGradient(cx, cy, 58 * d, 0x55FF2A3A, 0x00FF2A3A, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, 58 * d, glow);
            c.drawCircle(cx, cy, 46 * d, ring);
        }
    }
}
