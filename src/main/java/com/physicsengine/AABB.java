package com.physicsengine;
public class AABB {
    public double minX, minY, maxX, maxY;

    public AABB(double minX, double minY, double maxX, double maxY) {
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
    }

    // Checks if this box overlaps with another box
    public boolean intersects(AABB other) {
        return this.minX <= other.maxX && this.maxX >= other.minX &&
               this.minY <= other.maxY && this.maxY >= other.minY;
    }
}