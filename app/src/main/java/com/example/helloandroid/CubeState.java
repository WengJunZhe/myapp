package com.example.helloandroid;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class CubeState {
    // Standard order in Kociemba: U1..U9, R1..R9, F1..F9, D1..D9, L1..L9, B1..B9
    // Indices:
    // U: 0-8
    // R: 9-17
    // F: 18-26
    // D: 27-35
    // L: 36-44
    // B: 45-53
    private char[] state;

    public CubeState(String kociembaString) {
        if (kociembaString == null || kociembaString.length() != 54) {
            // Default solved state if invalid
            state = "UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB".toCharArray();
        } else {
            state = kociembaString.toCharArray();
        }
    }

    public char[] getState() {
        return state;
    }

    public void applyMove(String move) {
        if (move == null || move.isEmpty()) return;
        char face = move.charAt(0);
        int times = 1;
        if (move.length() > 1) {
            if (move.charAt(1) == '2') times = 2;
            else if (move.charAt(1) == '\'') times = 3;
        }

        for (int i = 0; i < times; i++) {
            rotate(face);
        }
    }

    private void rotate(char face) {
        switch (face) {
            case 'U': rotateU(); break;
            case 'D': rotateD(); break;
            case 'L': rotateL(); break;
            case 'R': rotateR(); break;
            case 'F': rotateF(); break;
            case 'B': rotateB(); break;
        }
    }

    private void rotateFaceOnly(int startIdx) {
        char[] temp = Arrays.copyOf(state, 54);
        // Corners
        state[startIdx + 0] = temp[startIdx + 6];
        state[startIdx + 2] = temp[startIdx + 0];
        state[startIdx + 8] = temp[startIdx + 2];
        state[startIdx + 6] = temp[startIdx + 8];
        // Edges
        state[startIdx + 1] = temp[startIdx + 3];
        state[startIdx + 5] = temp[startIdx + 1];
        state[startIdx + 7] = temp[startIdx + 5];
        state[startIdx + 3] = temp[startIdx + 7];
    }

    private void rotateU() {
        rotateFaceOnly(0);
        char[] t = Arrays.copyOf(state, 54);
        // F -> L, L -> B, B -> R, R -> F (Top layer)
        // Indices for top row: F(18,19,20), L(36,37,38), B(45,46,47), R(9,10,11)
        state[36] = t[18]; state[37] = t[19]; state[38] = t[20];
        state[45] = t[36]; state[46] = t[37]; state[47] = t[38];
        state[9]  = t[45]; state[10] = t[46]; state[11] = t[47];
        state[18] = t[9];  state[19] = t[10]; state[20] = t[11];
    }

    private void rotateD() {
        rotateFaceOnly(27);
        char[] t = Arrays.copyOf(state, 54);
        // F -> R, R -> B, B -> L, L -> F (Bottom layer)
        // Indices for bottom row: F(24,25,26), R(15,16,17), B(51,52,53), L(42,43,44)
        state[9+6] = t[18+6]; state[9+7] = t[18+7]; state[9+8] = t[18+8];
        state[45+6]= t[9+6];  state[45+7]= t[9+7];  state[45+8]= t[9+8];
        state[36+6]= t[45+6]; state[36+7]= t[45+7]; state[36+8]= t[45+8];
        state[18+6]= t[36+6]; state[18+7]= t[36+7]; state[18+8]= t[36+8];
    }

    private void rotateL() {
        rotateFaceOnly(36);
        char[] t = Arrays.copyOf(state, 54);
        // U -> F, F -> D, D -> B, B -> U (Left layer)
        // U(0,3,6), F(18,21,24), D(27,30,33), B(53,50,47) Note: B is reversed
        state[18] = t[0];  state[21] = t[3];  state[24] = t[6];
        state[27] = t[18]; state[30] = t[21]; state[33] = t[24];
        state[53] = t[27]; state[50] = t[30]; state[47] = t[33];
        state[0]  = t[53]; state[3]  = t[50]; state[6]  = t[47];
    }

    private void rotateR() {
        rotateFaceOnly(9);
        char[] t = Arrays.copyOf(state, 54);
        // U -> B, B -> D, D -> F, F -> U (Right layer)
        // U(2,5,8), B(51,48,45), D(29,32,35), F(20,23,26)
        state[51] = t[2];  state[48] = t[5];  state[45] = t[8];
        state[29] = t[51]; state[32] = t[48]; state[35] = t[45];
        state[20] = t[29]; state[23] = t[32]; state[26] = t[35];
        state[2]  = t[20]; state[5]  = t[23]; state[8]  = t[26];
    }

    private void rotateF() {
        rotateFaceOnly(18);
        char[] t = Arrays.copyOf(state, 54);
        // U -> R, R -> D, D -> L, L -> U
        // U(6,7,8), R(9,12,15), D(29,28,27), L(44,41,38)
        state[9]  = t[6];  state[12] = t[7];  state[15] = t[8];
        state[29] = t[9];  state[28] = t[12]; state[27] = t[15];
        state[44] = t[29]; state[41] = t[28]; state[38] = t[27];
        state[6]  = t[44]; state[7]  = t[41]; state[8]  = t[38];
    }

    private void rotateB() {
        rotateFaceOnly(45);
        char[] t = Arrays.copyOf(state, 54);
        // U -> L, L -> D, D -> R, R -> U
        // U(2,1,0), L(36,39,42), D(33,34,35), R(17,14,11)
        state[36] = t[2];  state[39] = t[1];  state[42] = t[0];
        state[33] = t[36]; state[34] = t[39]; state[35] = t[42];
        state[17] = t[33]; state[14] = t[34]; state[11] = t[35];
        state[2]  = t[17]; state[1]  = t[14]; state[0]  = t[11];
    }

    public static int getColorForChar(char c) {
        switch (c) {
            case 'U': return 0xFFFFFFFF; // White
            case 'R': return 0xFF00FF00; // Green (Wait, Python mapped G to R)
            case 'F': return 0xFFFF8C00; // Orange (O to F)
            case 'D': return 0xFFFFFF00; // Yellow
            case 'L': return 0xFF0000FF; // Blue (B to L)
            case 'B': return 0xFFFF0000; // Red (R to B)
            default: return 0xFF888888;
        }
    }
}
