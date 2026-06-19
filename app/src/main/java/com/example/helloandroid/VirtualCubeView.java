package com.example.helloandroid;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
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
    
    private float yaw = -45f;
    private float pitch = -25f;
    private float lastX, lastY;

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
        linePaint.setStrokeWidth(2f);

        arrowPaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setColor(0xFF00FF00);
        arrowPaint.setStrokeWidth(14f);
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
                lastX = x; lastY = y;
                performClick();
                break;
            case MotionEvent.ACTION_MOVE:
                // 修正 Yaw 旋轉方向以符合右手座標系與手勢直覺
                yaw -= (x - lastX) * 0.5f; 
                pitch += (y - lastY) * 0.5f;
                lastX = x; lastY = y;
                invalidate();
                break;
        }
        return true;
    }

    interface Renderable {
        float getZ();
        void draw(Canvas canvas, float scale);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (cubeState == null) return;

        int width = getWidth();
        int height = getHeight();
        float scale = Math.min(width, height) / 5f;
        canvas.translate(width / 2f, height / 2f);

        List<Renderable> scene = new ArrayList<>();
        char[] state = cubeState.getState();

        addStickers(scene, state);

        if (currentMove != null && !currentMove.isEmpty()) {
            addDashedArrowToScene(scene, currentMove);
        }

        float radYaw = (float) Math.toRadians(yaw);
        float radPitch = (float) Math.toRadians(pitch);

        for (Renderable r : scene) {
            if (r instanceof Sticker) ((Sticker) r).project(radYaw, radPitch);
            if (r instanceof ArrowSegment) ((ArrowSegment) r).project(radYaw, radPitch);
        }

        Collections.sort(scene, (o1, o2) -> Float.compare(o2.getZ(), o1.getZ()));

        for (Renderable r : scene) r.draw(canvas, scale);

        if (currentMove != null) postInvalidateOnAnimation();
    }

    private void addStickers(List<Renderable> scene, char[] state) {
        char moveFace = (currentMove != null && !currentMove.isEmpty()) ? currentMove.charAt(0) : ' ';
        // U: 0-8, R: 9-17, F: 18-26, D: 27-35, L: 36-44, B: 45-53
        for (int i = 0; i < 9; i++) scene.add(createSticker(state[i], -1+(i%3), 1.5f, -1+(i/3), 'U', moveFace));
        for (int i = 0; i < 9; i++) scene.add(createSticker(state[9+i], 1.5f, 1-(i/3), 1-(i%3), 'R', moveFace));
        for (int i = 0; i < 9; i++) scene.add(createSticker(state[18+i], -1+(i%3), 1-(i/3), 1.5f, 'F', moveFace));
        for (int i = 0; i < 9; i++) scene.add(createSticker(state[27+i], -1+(i%3), -1.5f, 1-(i/3), 'D', moveFace));
        for (int i = 0; i < 9; i++) scene.add(createSticker(state[36+i], -1.5f, 1-(i/3), -1+(i%3), 'L', moveFace));
        for (int i = 0; i < 9; i++) scene.add(createSticker(state[45+i], 1-(i%3), 1-(i/3), -1.5f, 'B', moveFace));
    }

    private Sticker createSticker(char colorChar, float cx, float cy, float cz, char face, char moveFace) {
        boolean active = false;
        switch (moveFace) {
            case 'U': active = (cy > 0.5f); break;
            case 'D': active = (cy < -0.5f); break;
            case 'L': active = (cx < -0.5f); break;
            case 'R': active = (cx > 0.5f); break;
            case 'F': active = (cz > 0.5f); break;
            case 'B': active = (cz < -0.5f); break;
        }
        Point3D[] pts = new Point3D[4];
        float g = 0.5f;
        switch(face) {
            case 'U': pts[0]=new Point3D(cx-g,cy,cz+g); pts[1]=new Point3D(cx+g,cy,cz+g); pts[2]=new Point3D(cx+g,cy,cz-g); pts[3]=new Point3D(cx-g,cy,cz-g); break;
            case 'D': pts[0]=new Point3D(cx-g,cy,cz-g); pts[1]=new Point3D(cx+g,cy,cz-g); pts[2]=new Point3D(cx+g,cy,cz+g); pts[3]=new Point3D(cx-g,cy,cz+g); break;
            case 'F': pts[0]=new Point3D(cx-g,cy-g,cz); pts[1]=new Point3D(cx+g,cy-g,cz); pts[2]=new Point3D(cx+g,cy+g,cz); pts[3]=new Point3D(cx-g,cy+g,cz); break;
            case 'B': pts[0]=new Point3D(cx+g,cy-g,cz); pts[1]=new Point3D(cx-g,cy-g,cz); pts[2]=new Point3D(cx-g,cy+g,cz); pts[3]=new Point3D(cx+g,cy+g,cz); break;
            case 'R': pts[0]=new Point3D(cx,cy-g,cz+g); pts[1]=new Point3D(cx,cy-g,cz-g); pts[2]=new Point3D(cx,cy+g,cz-g); pts[3]=new Point3D(cx,cy+g,cz+g); break;
            case 'L': pts[0]=new Point3D(cx,cy-g,cz-g); pts[1]=new Point3D(cx,cy-g,cz+g); pts[2]=new Point3D(cx,cy+g,cz+g); pts[3]=new Point3D(cx,cy+g,cz-g); break;
        }
        return new Sticker(pts, colorChar, active);
    }

    private void addDashedArrowToScene(List<Renderable> scene, String move) {
        char face = move.charAt(0);
        boolean prime = move.contains("'");
        boolean doubleMove = move.contains("2");
        float r = 2.4f, h;
        int steps = 24;
        float sweep = (doubleMove ? 180f : 90f) * (prime ? 1 : -1);
        float startAng = 30f;
        for (int i = 0; i < steps; i++) {
            if (i % 2 != 0) continue; 
            float a1 = (float) Math.toRadians(startAng + sweep * i / steps);
            float a2 = (float) Math.toRadians(startAng + sweep * (i+1) / steps);
            Point3D p1, p2;
            switch(face) {
                case 'U': h=1.6f;  p1=new Point3D((float)Math.cos(a1)*r,h,-(float)Math.sin(a1)*r); p2=new Point3D((float)Math.cos(a2)*r,h,-(float)Math.sin(a2)*r); break;
                case 'D': h=-1.6f; p1=new Point3D((float)Math.cos(a1)*r,h,(float)Math.sin(a1)*r); p2=new Point3D((float)Math.cos(a2)*r,h,(float)Math.sin(a2)*r); break;
                case 'R': h=1.6f;  p1=new Point3D(h,(float)Math.cos(a1)*r,-(float)Math.sin(a1)*r); p2=new Point3D(h,(float)Math.cos(a2)*r,-(float)Math.sin(a2)*r); break;
                case 'L': h=-1.6f; p1=new Point3D(h,(float)Math.cos(a1)*r,(float)Math.sin(a1)*r); p2=new Point3D(h,(float)Math.cos(a2)*r,(float)Math.sin(a2)*r); break;
                case 'F': h=1.6f;  p1=new Point3D((float)Math.cos(a1)*r,(float)Math.sin(a1)*r,h); p2=new Point3D((float)Math.cos(a2)*r,(float)Math.sin(a2)*r,h); break;
                case 'B': h=-1.6f; p1=new Point3D(-(float)Math.cos(a1)*r,(float)Math.sin(a1)*r,h); p2=new Point3D(-(float)Math.cos(a2)*r,(float)Math.sin(a2)*r,h); break;
                default: continue;
            }
            scene.add(new ArrowSegment(p1, p2, i >= steps - 2));
        }
    }

    private static class Point3D {
        float x, y, z;
        Point3D(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }
        Point3D rotate(float yaw, float pitch) {
            // 標準右手座標系旋轉
            float x1 = x * (float) Math.cos(yaw) - z * (float) Math.sin(yaw);
            float z1 = x * (float) Math.sin(yaw) + z * (float) Math.cos(yaw);
            float y2 = y * (float) Math.cos(pitch) - z1 * (float) Math.sin(pitch);
            float z2 = y * (float) Math.sin(pitch) + z1 * (float) Math.cos(pitch);
            return new Point3D(x1, y2, z2);
        }
    }

    private class Sticker implements Renderable {
        Point3D[] pts, rot;
        int color;
        float avgZ, normalZ;
        boolean active;

        Sticker(Point3D[] pts, char c, boolean active) {
            this.pts = pts;
            this.color = CubeState.getColorForChar(c);
            this.rot = new Point3D[4];
            this.active = active;
        }

        void project(float yaw, float pitch) {
            avgZ = 0;
            for (int i = 0; i < 4; i++) { rot[i] = pts[i].rotate(yaw, pitch); avgZ += rot[i].z; }
            avgZ /= 4f;
            // 配合 Android -Y 投影的 2D 法向判定
            normalZ = (rot[1].x - rot[0].x) * (rot[2].y - rot[0].y) - (rot[1].y - rot[0].y) * (rot[2].x - rot[0].x);
        }

        @Override public float getZ() { return avgZ; }
        @Override public void draw(Canvas canvas, float scale) {
            if (normalZ <= 0) return;
            stickerPaint.setColor(0xFF111111);
            Path p = new Path();
            p.moveTo(rot[0].x * scale, -rot[0].y * scale);
            for (int i = 1; i < 4; i++) p.lineTo(rot[i].x * scale, -rot[i].y * scale);
            p.close();
            canvas.drawPath(p, stickerPaint);

            int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
            float shade = 0.6f + 0.4f * Math.max(0, normalZ / 2f);
            if (!active && currentMove != null) shade *= 0.65f; // 調整為 65% 亮度
            stickerPaint.setColor(Color.rgb((int)(r*shade), (int)(g*shade), (int)(b*shade)));

            float shrink = 0.9f;
            Path pSmall = new Path();
            float midX = (rot[0].x + rot[2].x) / 2f;
            float midY = (rot[0].y + rot[2].y) / 2f;
            pSmall.moveTo((midX + (rot[0].x - midX) * shrink) * scale, -(midY + (rot[0].y - midY) * shrink) * scale);
            for (int i = 1; i < 4; i++) {
                pSmall.lineTo((midX + (rot[i].x - midX) * shrink) * scale, -(midY + (rot[i].y - midY) * shrink) * scale);
            }
            pSmall.close();
            canvas.drawPath(pSmall, stickerPaint);
        }
    }

    private class ArrowSegment implements Renderable {
        Point3D p1, p2, r1, r2;
        boolean head;
        float avgZ;
        ArrowSegment(Point3D p1, Point3D p2, boolean head) { this.p1 = p1; this.p2 = p2; this.head = head; }
        void project(float yaw, float pitch) {
            r1 = p1.rotate(yaw, pitch); r2 = p2.rotate(yaw, pitch);
            avgZ = (r1.z + r2.z) / 2f;
        }
        @Override public float getZ() { return avgZ + 0.2f; }
        @Override public void draw(Canvas canvas, float scale) {
            float phase = (System.currentTimeMillis() % 800) / 800f * 40f;
            arrowPaint.setPathEffect(new DashPathEffect(new float[]{25, 15}, phase));
            canvas.drawLine(r1.x * scale, -r1.y * scale, r2.x * scale, -r2.y * scale, arrowPaint);
            if (head) {
                arrowPaint.setPathEffect(null);
                float x1 = r1.x * scale, y1 = -r1.y * scale, x2 = r2.x * scale, y2 = -r2.y * scale;
                float ang = (float) Math.atan2(y2 - y1, x2 - x1);
                canvas.drawLine(x2, y2, x2 - 35f*(float)Math.cos(ang-0.5f), y2 - 35f*(float)Math.sin(ang-0.5f), arrowPaint);
                canvas.drawLine(x2, y2, x2 - 35f*(float)Math.cos(ang+0.5f), y2 - 35f*(float)Math.sin(ang+0.5f), arrowPaint);
            }
        }
    }
}
