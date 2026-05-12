package com.physicsengine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpatialHash {
    private double cellSize;
    // Changed String to Long for zero-allocation keys
    private Map<Long, List<Ball>> grid;

    public SpatialHash(double initialCellSize) {
        this.cellSize = initialCellSize;
        this.grid = new HashMap<>();
    }

    public void setCellSize(double cellSize) {
        this.cellSize = Math.max(cellSize, 10.0);
    }

    public void clear() {
        grid.clear();
    }

    public void insert(Ball ball) {
        double r = ball.getRadius();
        int minX = (int) Math.floor((ball.getPosition().x - r) / cellSize);
        int maxX = (int) Math.floor((ball.getPosition().x + r) / cellSize);
        int minY = (int) Math.floor((ball.getPosition().y - r) / cellSize);
        int maxY = (int) Math.floor((ball.getPosition().y + r) / cellSize);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                // Bitwise shift to pack two 32-bit ints into one 64-bit long
                long key = (((long) x) << 32) | (y & 0xffffffffL);
                grid.computeIfAbsent(key, k -> new ArrayList<>()).add(ball);
            }
        }
    }

    public Map<Long, List<Ball>> getGrid() { return grid; }
}