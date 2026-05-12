package sandbox.system;
import sandbox.core.*;
import sandbox.physics.*;
import sandbox.physics.constraints.*;
import sandbox.logic.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

import java.nio.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class LWJGLRenderer {
    private long window;
    private int width, height;

    public void init(int width, int height) {
        this.width = width;
        this.height = height;

        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        window = glfwCreateWindow(width, height, "Physics Logic Sandbox (LWJGL)", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Failed to create the GLFW window");

        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) glfwSetWindowShouldClose(window, true);
        });

        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(window, pWidth, pHeight);
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            glfwSetWindowPos(window, (vidmode.width() - pWidth.get(0)) / 2, (vidmode.height() - pHeight.get(0)) / 2);
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
        GL.createCapabilities();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    public boolean shouldClose() { return glfwWindowShouldClose(window); }

    public void render(EngineContainer engine, double camX, double camY, double zoom) {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glClearColor(0.06f, 0.125f, 0.15f, 1.0f);

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(-width / (2.0 * zoom) + camX, width / (2.0 * zoom) + camX,
                height / (2.0 * zoom) + camY, -height / (2.0 * zoom) + camY, -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        // Render balls
        for (Ball b : engine.getBalls()) {
            renderBall(b);
        }

        // Render walls
        glColor4f(0.125f, 0.227f, 0.263f, 1.0f);
        glLineWidth(4.0f);
        glBegin(GL_LINES);
        for (WallBody w : engine.getWalls()) {
            glVertex2d(w.p1.x, w.p1.y);
            glVertex2d(w.p2.x, w.p2.y);
        }
        glEnd();

        glfwSwapBuffers(window);
        glfwPollEvents();
    }

    private void renderBall(Ball b) {
        if (b.isDestroyed) return;

        // Set color based on material/logic
        if (b.logicNode != null && b.logicNode.currentState) glColor4f(0, 1, 0.2f, 1);
        else if (b.isStatic) glColor4f(0.9f, 0.3f, 0.23f, 1);
        else glColor4f(0.85f, 0.65f, 0.0f, 1.0f);

        if (b.shape == Ball.ShapeType.CIRCLE) {
            drawCircle(b.getPosition().x, b.getPosition().y, b.getRadius());
        } else {
            Vector2D[] verts = b.getTransformedVertices();
            if (verts != null) {
                glBegin(GL_POLYGON);
                for (Vector2D v : verts) glVertex2d(v.x, v.y);
                glEnd();

                glColor4f(0, 0, 0, 0.5f);
                glLineWidth(1.0f);
                glBegin(GL_LINE_LOOP);
                for (Vector2D v : verts) glVertex2d(v.x, v.y);
                glEnd();
            }
        }
    }

    private void drawCircle(double x, double y, double r) {
        glBegin(GL_POLYGON);
        for (int i = 0; i < 32; i++) {
            double angle = i * 2.0 * Math.PI / 32;
            glVertex2d(x + Math.cos(angle) * r, y + Math.sin(angle) * r);
        }
        glEnd();
    }

    public void cleanup() {
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }
}
