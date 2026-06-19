package com.example.helloandroid;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VirtualCubeView extends View {
    private CubeState cubeState;
    private String currentMove;
    private final Paint stickerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    
    // 調整初始角度：Yaw -45度讓 F(橘)在左，R(綠)在右
    private float yaw = -45f;
    private float pitch = -25f;
    private float lastX, lastY;
    private float scale = 100f;

    public VirtualCubeView(Context context) {
        super(context);
        init();
    }

    public VirtualCubeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        stickerPaint.setStyle(Paint.Style.FILL);
        
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setColor(Color.BLACK);
        linePaint.setStrokeWidth(3f);

        arrowPaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setColor(0xFFFF00FF); // 螢光粉，保證清晰
        arrowPaint.setStrokeWidth(12f);
        arrowPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setCubeState(CubeState cubeState) {
        this.cubeState = cubeState;
        invalidate();
    }

    public void setCurrentMove(String move) {
        this.currentMove = move;
        invalidate();
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastX = x;
                lastY = y;
                performClick();
                break;
            case MotionEvent.ACTION_MOVE:
                yaw += (x - lastX) * 0.5f; 
                pitch += (y - lastY) * 0.5f;
                lastX = x;
                lastY = y;
                invalidate();
                break;
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (cubeState == null) return;

        int width = getWidth();
        int height = getHeight();
        scale = Math.min(width, height) / 5f;
        canvas.translate(width / 2f, height / 2f);

        List<Sticker> stickers = new ArrayList<>();
        char[] state = cubeState.getState();

        // 核心修正：所有面的頂點順序改為 CCW (逆時針)，確保正面判定正確
        
        // U: 0-8 (White)
        for (int i = 0; i < 9; i++) {
            float x = -1f + (i % 3);
            float z = -1f + (i / 3);
            stickers.add(new Sticker(new Point3D[]{
                    new Point3D(x - 0.5f, 1.5f, z + 0.5f), new Point3D(x + 0.5f, 1.5f, z + 0.5f),
                    new Point3D(x + 0.5f, 1.5f, z - 0.5f), new Point3D(x - 0.5f, 1.5f, z - 0.5f)
            }, state[i]));
        }
        // R: 9-17 (Green)
        for (int i = 0; i < 9; i++) {
            float z = 1f - (i % 3);
            float y = 1f - (i / 3);
            stickers.add(new Sticker(new Point3D[]{
                    new Point3D(1.5f, y - 0.5f, z + 0.5f), new Point3D(1.5f, y - 0.5f, z - 0.5f),
                    new Point3D(1.5f, y + 0.5f, z - 0.5f), new Point3D(1.5f, y + 0.5f, z + 0.5f)
            }, state[9 + i]));
        }
        // F: 18-26 (Orange)
        for (int i = 0; i < 9; i++) {
            float x = -1f + (i % 3);
            float y = 1f - (i / 3);
            stickers.add(new Sticker(new Point3D[]{
                    new Point3D(x - 0.5f, y - 0.5f, 1.5f), new Point3D(x + 0.5f, y - 0.5f, 1.5f),
                    new Point3D(x + 0.5f, y + 0.5f, 1.5f), new Point3D(x - 0.5f, y + 0.5f, 1.5f)
            }, state[18 + i]));
        }
        // D: 27-35 (Yellow)
        for (int i = 0; i < 9; i++) {
            float x = -1f + (i % 3);
            float z = 1f - (i / 3);
            stickers.add(new Sticker(new Point3D[]{
                    new Point3D(x - 0.5f, -1.5f, z - 0.5f), new Point3D(x + 0.5f, -1.5f, z - 0.5f),
                    new Point3D(x + 0.5f, -1.5f, z + 0.5f), new Point3D(x - 0.5f, -1.5f, z + 0.5f)
            }, state[27 + i]));
        }
        // L: 36-44 (Blue)
        for (int i = 0; i < 9; i++) {
            float z = -1f + (i % 3);
            float y = 1f - (i / 3);
            stickers.add(new Sticker(new Point3D[]{
                    new Point3D(-1.5f, y - 0.5f, z - 0.5f), new Point3D(-1.5f, y - 0.5f, z + 0.5f),
                    new Point3D(-1.5f, y + 0.5f, z + 0.5f), new Point3D(-1.5f, y + 0.5f, z - 0.5f)
            }, state[36 + i]));
        }
        // B: 45-53 (Red)
        for (int i = 0; i < 9; i++) {
            float x = 1f - (i % 3);
            float y = 1f - (i / 3);
            stickers.add(new Sticker(new Point3D[]{
                    new Point3D(x + 0.5f, y - 0.5f, -1.5f), new Point3D(x - 0.5f, y - 0.5f, -1.5f),
                    new Point3D(x - 0.5f, y + 0.5f, -1.5f), new Point3D(x + 0.5f, y + 0.5f, -1.5f)
            }, state[45 + i]));
        }

        float radYaw = (float) Math.toRadians(yaw);
        float radPitch = (float) Math.toRadians(pitch);

        for (Sticker s : stickers) {
            s.project(radYaw, radPitch);
        }

        Collections.sort(stickers, (o1, o2) -> Float.compare(o2.avgZ, o1.avgZ));

        for (Sticker s : stickers) {
            if (s.normalZ > 0) { // 現在 normalZ > 0 代表面向攝影機的正面
                s.draw(canvas, stickerPaint, linePaint, scale);
            }
        }

        if (currentMove != null && !currentMove.isEmpty()) {
            drawMoveArrow(canvas, currentMove, radYaw, radPitch);
        }
    }

    private void drawMoveArrow(Canvas canvas, String move, float radYaw, float radPitch) {
        char face = move.charAt(0);
        boolean prime = move.contains("'");
        boolean doubleMove = move.contains("2");
        
        List<Point3D> arrowPoints = new ArrayList<>();
        float r = 2.0f; 
        float h;
        
        switch (face) {
            case 'U': h = 1.6f; addCirclePoints(arrowPoints, 'y', h, r, prime, doubleMove); break;
            case 'D': h = -1.6f; addCirclePoints(arrowPoints, 'y', h, r, !prime, doubleMove); break;
            case 'L': h = -1.6f; addCirclePoints(arrowPoints, 'x', h, r, prime, doubleMove); break;
            case 'R': h = 1.6f; addCirclePoints(arrowPoints, 'x', h, r, !prime, doubleMove); break;
            case 'F': h = 1.6f; addCirclePoints(arrowPoints, 'z', h, r, !prime, doubleMove); break;
            case 'B': h = -1.6f; addCirclePoints(arrowPoints, 'z', h, r, prime, doubleMove); break;
        }

        if (arrowPoints.isEmpty()) return;

        Path path = new Path();
        for (int i = 0; i < arrowPoints.size(); i++) {
            Point3D p = arrowPoints.get(i).rotate(radYaw, radPitch);
            if (i == 0) path.moveTo(p.x * scale, -p.y * scale);
            else path.lineTo(p.x * scale, -p.y * scale);
        }
        canvas.drawPath(path, arrowPaint);
        
        if (arrowPoints.size() > 1) {
            Point3D pLast = arrowPoints.get(arrowPoints.size() - 1).rotate(radYaw, radPitch);
            Point3D pPrev = arrowPoints.get(arrowPoints.size() - 2).rotate(radYaw, radPitch);
            drawArrowHead(canvas, pPrev.x * scale, -pPrev.y * scale, pLast.x * scale, -pLast.y * scale);
        }
    }

    private void addCirclePoints(List<Point3D> points, char axis, float h, float r, boolean reverse, boolean doubleMove) {
        float startAngle = 30f;
        float sweepAngle = doubleMove ? 180f : 90f;
        if (reverse) sweepAngle = -sweepAngle;
        
        int steps = 20;
        for (int i = 0; i <= steps; i++) {
            float angle = (float) Math.toRadians(startAngle + sweepAngle * i / steps);
            float c = (float) Math.cos(angle) * r;
            float s = (float) Math.sin(angle) * r;
            if (axis == 'y') points.add(new Point3D(c, h, s));
            else if (axis == 'x') points.add(new Point3D(h, c, s));
            else if (axis == 'z') points.add(new Point3D(c, s, h));
        }
    }

    private void drawArrowHead(Canvas canvas, float x1, float y1, float x2, float y2) {
        float angle = (float) Math.atan2(y2 - y1, x2 - x1);
        float headLen = 30f;
        canvas.drawLine(x2, y2, x2 - headLen * (float) Math.cos(angle - Math.PI / 6), y2 - headLen * (float) Math.sin(angle - Math.PI / 6), arrowPaint);
        canvas.drawLine(x2, y2, x2 - headLen * (float) Math.cos(angle + Math.PI / 6), y2 - headLen * (float) Math.sin(angle + Math.PI / 6), arrowPaint);
    }

    private static class Point3D {
        float x, y, z;
        Point3D(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }
        
        Point3D rotate(float yaw, float pitch) {
            // Standard Rotation Formula
            float x1 = x * (float) Math.cos(yaw) + z * (float) Math.sin(yaw);
            float z1 = -x * (float) Math.sin(yaw) + z * (float) Math.cos(yaw);
            float y2 = y * (float) Math.cos(pitch) - z1 * (float) Math.sin(pitch);
            float z2 = y * (float) Math.sin(pitch) + z1 * (float) Math.cos(pitch);
            return new Point3D(x1, y2, z2);
        }
    }

    private static class Sticker {
        Point3D[] pts;
        Point3D[] rotatedPts;
        int color;
        float avgZ;
        float normalZ;

        Sticker(Point3D[] pts, char c) {
            this.pts = pts;
            this.color = CubeState.getColorForChar(c);
            this.rotatedPts = new Point3D[4];
        }

        void project(float yaw, float pitch) {
            avgZ = 0;
            for (int i = 0; i < 4; i++) {
                rotatedPts[i] = pts[i].rotate(yaw, pitch);
                avgZ += rotatedPts[i].z;
            }
            avgZ /= 4f;
            
            // 2D Winding calculation
            float v1x = rotatedPts[1].x - rotatedPts[0].x;
            float v1y = rotatedPts[1].y - rotatedPts[0].y;
            float v2x = rotatedPts[2].x - rotatedPts[0].x;
            float v2y = rotatedPts[2].y - rotatedPts[0].y;
            normalZ = v1x * v2y - v1y * v2x;
        }

        void draw(Canvas canvas, Paint fill, Paint stroke, float scale) {
            Path path = new Path();
            path.moveTo(rotatedPts[0].x * scale, -rotatedPts[0].y * scale);
            for (int i = 1; i < 4; i++) {
                path.lineTo(rotatedPts[i].x * scale, -rotatedPts[i].y * scale);
            }
            path.close();
            
            int r = Color.red(color);
            int g = Color.green(color);
            int b = Color.blue(color);
            float shading = 0.8f + 0.2f * Math.max(0, normalZ / 2f); 
            fill.setColor(Color.rgb((int)(r*shading), (int)(g*shading), (int)(b*shading)));
            
            canvas.drawPath(path, fill);
            canvas.drawPath(path, stroke);
        }
    }
}
