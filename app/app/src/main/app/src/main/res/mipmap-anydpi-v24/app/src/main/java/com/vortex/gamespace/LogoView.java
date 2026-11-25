package com.vortex.gamespace;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

public class LogoView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public LogoView(Context c) {
        super(c);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight());
        float ox = (getWidth() - s) / 2f, oy = (getHeight() - s) / 2f;
        float m = s * 0.2f;
        p.setStrokeWidth(s * 0.15f);
        p.setColor(Color.WHITE);
        c.drawLine(ox + m, oy + m, ox + s - m, oy + s - m, p);
        p.setColor(0xFFFF2A3A);
        c.drawLine(ox + s - m, oy + m, ox + m, oy + s - m, p);
    }
}
