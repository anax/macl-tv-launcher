package com.macl.tvagent;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/** The floating circle: a quiet dot when idle, a turning arc while the agent works. */
public final class OrbView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private final float density;
    private boolean working;

    public OrbView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        setContentDescription(context.getString(R.string.orb_description));
        setClickable(true);
        setFocusable(false);
    }

    public void setWorking(boolean working) {
        if (this.working == working) return;
        this.working = working;
        invalidate();
        if (working) postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) / 2f - 2 * density;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x337C5CFF);
        canvas.drawCircle(cx, cy, radius + 3 * density, paint);

        paint.setColor(0xFF7C5CFF);
        canvas.drawCircle(cx, cy, radius, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * density);
        paint.setColor(0x66FFFFFF);
        canvas.drawCircle(cx, cy, radius - 1.5f * density, paint);

        if (working) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4 * density);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(Color.WHITE);
            float inner = radius * 0.55f;
            arc.set(cx - inner, cy - inner, cx + inner, cy + inner);
            canvas.drawArc(arc, (SystemClock.uptimeMillis() / 7f) % 360f, 110f, false, paint);
            postInvalidateOnAnimation();
        } else {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(cx, cy, radius * 0.28f, paint);
        }
    }
}
