package com.example.helloandroid;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class VirtualCubeView extends View {
    private CubeState cubeState;
    private Paint paint;
    private float cellSize;

    public VirtualCubeView(Context context) {
        super(context);
        init();
    }

    public VirtualCubeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(2f);
    }

    public void setCubeState(CubeState cubeState) {
        this.cubeState = cubeState;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (cubeState == null) return;

        int width = getWidth();
        int height = getHeight();
        // 2D Net layout:
        //      U
        //    L F R B
        //      D
        // Max 4 cells wide, 3 cells high (each cell is a 3x3 face)
        // Total grid is 4x3 faces.
        cellSize = Math.min(width / 12f, height / 9f);
        float offsetX = (width - cellSize * 12) / 2f;
        float offsetY = (height - cellSize * 9) / 2f;

        drawFace(canvas, 0, offsetX + 3 * cellSize, offsetY, "U");      // U
        drawFace(canvas, 36, offsetX, offsetY + 3 * cellSize, "L");     // L
        drawFace(canvas, 18, offsetX + 3 * cellSize, offsetY + 3 * cellSize, "F"); // F
        drawFace(canvas, 9, offsetX + 6 * cellSize, offsetY + 3 * cellSize, "R");  // R
        drawFace(canvas, 45, offsetX + 9 * cellSize, offsetY + 3 * cellSize, "B"); // B
        drawFace(canvas, 27, offsetX + 3 * cellSize, offsetY + 6 * cellSize, "D"); // D
    }

    private void drawFace(Canvas canvas, int startIdx, float x, float y, String label) {
        char[] state = cubeState.getState();
        for (int i = 0; i < 9; i++) {
            int row = i / 3;
            int col = i % 3;
            paint.setColor(CubeState.getColorForChar(state[startIdx + i]));
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRect(x + col * cellSize, y + row * cellSize, 
                           x + (col + 1) * cellSize, y + (row + 1) * cellSize, paint);
            
            paint.setColor(Color.BLACK);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawRect(x + col * cellSize, y + row * cellSize, 
                           x + (col + 1) * cellSize, y + (row + 1) * cellSize, paint);
        }
    }
}
