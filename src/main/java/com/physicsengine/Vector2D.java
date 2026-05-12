package com.physicsengine;
public class Vector2D {
    public final double x, y;

    public Vector2D(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public Vector2D add(Vector2D v) { return new Vector2D(x + v.x, y + v.y); }
    public Vector2D subtract(Vector2D v) { return new Vector2D(x - v.x, y - v.y); }
    public Vector2D multiply(double scalar) { return new Vector2D(x * scalar, y * scalar); }
    public double dot(Vector2D v) { return x * v.x + y * v.y; }
    public double cross(Vector2D v) { return x * v.y - y * v.x; }
    
    public double lengthSquared() { return x * x + y * y; }
    public double length() { return Math.sqrt(x * x + y * y); }
    public double magnitude() { return Math.sqrt(x * x + y * y); }
    
    public Vector2D normalize() {
        double len = length();
        if (len == 0) return new Vector2D(0, 0);
        return new Vector2D(x / len, y / len);
    }
    
    public Vector2D rotate(double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vector2D(x * cos - y * sin, x * sin + y * cos);
    }

    public double distanceTo(Vector2D v) { return subtract(v).length(); }
    public double distanceSquared(Vector2D v) { return subtract(v).lengthSquared(); }

    // Projects vertices onto this axis and returns a double array [min, max]
    public double[] projectVertices(Vector2D[] vertices) {
        double min = this.dot(vertices[0]);
        double max = min;
        for (int i = 1; i < vertices.length; i++) {
            double p = this.dot(vertices[i]);
            if (p < min) min = p;
            if (p > max) max = p;
        }
        return new double[] { min, max };
    }
}