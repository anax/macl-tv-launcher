package com.macl.tvagent;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * Creative animated status indicator for Zenith:
 * - IDLE: Gentle ambient cyan breathing glow
 * - THINKING / PLANNING: Celestial orbit arc + pulsating dual-tone core (Cyan/Violet)
 * - EXECUTING: Counter-rotating amber reactor gear arcs
 * - ANSWERING: Live 3-bar audio wave equalizer in emerald green
 * - ERROR: Warning pulse in crimson
 */
public final class OrbView extends View {
    public static final int STATE_IDLE = 0;
    public static final int STATE_THINKING = 1;
    public static final int STATE_EXECUTING = 2;
    public static final int STATE_ANSWERING = 3;
    public static final int STATE_ERROR = 4;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();
    private final RectF barRect = new RectF();
    private final float density;

    private int state = STATE_IDLE;
    private int accentColor = 0xFF00B2E3;

    public OrbView(Context context) {
        this(context, null);
    }

    public OrbView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        setClickable(false);
        setFocusable(false);
    }

    public void setState(int newState) {
        int color;
        switch (newState) {
            case STATE_THINKING:
                color = 0xFF00B2E3;
                break;
            case STATE_EXECUTING:
                color = 0xFFFFC629;
                break;
            case STATE_ANSWERING:
                color = 0xFF10B981;
                break;
            case STATE_ERROR:
                color = 0xFFFF5252;
                break;
            case STATE_IDLE:
            default:
                color = 0xFF00B2E3;
                break;
        }
        setState(newState, color);
    }

    public void setState(int newState, int color) {
        this.state = newState;
        this.accentColor = color;
        postInvalidateOnAnimation();
    }

    public void setWorking(boolean working) {
        setState(working ? STATE_THINKING : STATE_IDLE);
    }

    public int getState() {
        return state;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float maxR = Math.min(getWidth(), getHeight()) / 2f - 1f * density;
        long now = SystemClock.uptimeMillis();

        switch (state) {
            case STATE_THINKING:
                drawThinking(canvas, cx, cy, maxR, now);
                break;
            case STATE_EXECUTING:
                drawExecuting(canvas, cx, cy, maxR, now);
                break;
            case STATE_ANSWERING:
                drawAnswering(canvas, cx, cy, maxR, now);
                break;
            case STATE_ERROR:
                drawError(canvas, cx, cy, maxR, now);
                break;
            case STATE_IDLE:
            default:
                drawIdle(canvas, cx, cy, maxR, now);
                break;
        }

        // Keep animating smoothly at 60 FPS while active
        postInvalidateOnAnimation();
    }

    /** IDLE: Soft ambient breathing halo + calm cyan center */
    private void drawIdle(Canvas canvas, float cx, float cy, float maxR, long now) {
        float breath = (float) (Math.sin(now * 0.0022) * 0.5 + 0.5); // 2.8s period
        float haloR = maxR * (0.75f + 0.25f * breath);

        // Ambient outer glow
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(accentColor);
        paint.setAlpha((int) (28 + 35 * breath));
        canvas.drawCircle(cx, cy, haloR, paint);

        // Core dot
        paint.setAlpha(255);
        canvas.drawCircle(cx, cy, maxR * 0.5f, paint);

        // Pinpoint white center
        paint.setColor(0xFFFFFFFF);
        canvas.drawCircle(cx, cy, maxR * 0.22f, paint);
    }

    /** THINKING / PLANNING: Celestial orbit arc + violet/cyan pulsating core */
    private void drawThinking(Canvas canvas, float cx, float cy, float maxR, long now) {
        float pulse = (float) (Math.sin(now * 0.007) * 0.5 + 0.5); // fast pulse
        float orbitAngle = (now * 0.42f) % 360f; // 850ms per rotation

        // Glowing outer aura
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF7C5CFF); // Electric violet aura
        paint.setAlpha((int) (40 + 50 * pulse));
        canvas.drawCircle(cx, cy, maxR, paint);

        // Inner glowing core
        paint.setColor(accentColor); // Cyan core
        paint.setAlpha(240);
        canvas.drawCircle(cx, cy, maxR * (0.35f + 0.12f * pulse), paint);

        // Orbiting glowing ring arc
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(2f * density);
        paint.setColor(0xFF00B2E3);
        arcRect.set(cx - maxR + 1.5f * density, cy - maxR + 1.5f * density,
                cx + maxR - 1.5f * density, cy + maxR - 1.5f * density);
        canvas.drawArc(arcRect, orbitAngle, 100f, false, paint);

        // Orbiting satellite bead at trailing edge
        double rad = Math.toRadians(orbitAngle + 100f);
        float satX = (float) (cx + (maxR - 1.5f * density) * Math.cos(rad));
        float satY = (float) (cy + (maxR - 1.5f * density) * Math.sin(rad));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
        canvas.drawCircle(satX, satY, 1.8f * density, paint);
    }

    /** EXECUTING: Counter-rotating amber reactor gear arcs */
    private void drawExecuting(Canvas canvas, float cx, float cy, float maxR, long now) {
        float angleCW = (now * 0.65f) % 360f;
        float angleCCW = 360f - ((now * 0.45f) % 360f);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(2.2f * density);
        paint.setColor(0xFFFFC629); // Amber

        // Outer 3-segment gear arc
        arcRect.set(cx - maxR + 1.2f * density, cy - maxR + 1.2f * density,
                cx + maxR - 1.2f * density, cy + maxR - 1.2f * density);
        canvas.drawArc(arcRect, angleCW, 55f, false, paint);
        canvas.drawArc(arcRect, angleCW + 120f, 55f, false, paint);
        canvas.drawArc(arcRect, angleCW + 240f, 55f, false, paint);

        // Inner counter-rotating ring
        float innerR = maxR * 0.55f;
        arcRect.set(cx - innerR, cy - innerR, cx + innerR, cy + innerR);
        paint.setStrokeWidth(1.8f * density);
        paint.setColor(0xFFFFFFFF);
        canvas.drawArc(arcRect, angleCCW, 90f, false, paint);
        canvas.drawArc(arcRect, angleCCW + 180f, 90f, false, paint);
    }

    /** ANSWERING / SPEAKING: Live 3-bar voice equalizer visualizer */
    private void drawAnswering(Canvas canvas, float cx, float cy, float maxR, long now) {
        // 3 dancing sound equalizer bars
        float h1 = (float) (Math.sin(now * 0.014) * 0.5 + 0.5);
        float h2 = (float) (Math.sin(now * 0.021 + 1.3) * 0.5 + 0.5);
        float h3 = (float) (Math.sin(now * 0.017 + 2.7) * 0.5 + 0.5);

        float barW = 2.4f * density;
        float maxH = maxR * 1.6f;
        float minH = 3f * density;
        float barSpacing = 4f * density;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF10B981); // Emerald Green

        // Bar 1 (Left)
        float b1H = minH + (maxH - minH) * h1;
        barRect.set(cx - barSpacing - barW / 2f, cy - b1H / 2f, cx - barSpacing + barW / 2f, cy + b1H / 2f);
        canvas.drawRoundRect(barRect, 1.2f * density, 1.2f * density, paint);

        // Bar 2 (Center)
        float b2H = minH + (maxH - minH) * h2;
        barRect.set(cx - barW / 2f, cy - b2H / 2f, cx + barW / 2f, cy + b2H / 2f);
        paint.setColor(0xFF34D399); // Brighter center wave
        canvas.drawRoundRect(barRect, 1.2f * density, 1.2f * density, paint);

        // Bar 3 (Right)
        float b3H = minH + (maxH - minH) * h3;
        barRect.set(cx + barSpacing - barW / 2f, cy - b3H / 2f, cx + barSpacing + barW / 2f, cy + b3H / 2f);
        paint.setColor(0xFF10B981);
        canvas.drawRoundRect(barRect, 1.2f * density, 1.2f * density, paint);
    }

    /** ERROR: Rapid warning pulse */
    private void drawError(Canvas canvas, float cx, float cy, float maxR, long now) {
        float flash = (float) (Math.sin(now * 0.025) * 0.5 + 0.5);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFF5252);
        paint.setAlpha((int) (120 + 135 * flash));
        canvas.drawCircle(cx, cy, maxR * (0.6f + 0.35f * flash), paint);

        paint.setColor(0xFFFFFFFF);
        paint.setAlpha(255);
        canvas.drawCircle(cx, cy, maxR * 0.25f, paint);
    }
}
