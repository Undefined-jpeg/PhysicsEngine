# Physics Logic Sandbox

A 2D physics and logic sandbox engine implemented in Java.

## Features
- **Phased Roadmap Implementation:**
    - **Phase 1:** Undo/Redo, Selection & Grouping, JSON Prefabs, Bezier Wires.
    - **Phase 2:** Prismatic Joints, Revolute Joint Limits, Proximity & Speed Sensors.
    - **Phase 3:** Multithreaded Engine, OpenAL Audio, LWJGL Hardware Acceleration foundation.
    - **Phase 4:** Soft Body Physics, Aerodynamics (Lift/Drag), Buoyancy (Water Zones).

## Dependencies
This project requires the following libraries:
- **Gson**: For JSON serialization of prefabs.
- **LWJGL 3**: For hardware-accelerated rendering and OpenAL audio.

### Setup Instructions
1. **Download Gson:**
   Download `gson-2.10.1.jar` from [Maven Central](https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar) and place it in the root directory.

2. **Download LWJGL:**
   The easiest way to get the correct LWJGL libraries for your operating system is to use the [LWJGL Customizer](https://www.lwjgl.org/customize).
   - Select **Release** version (e.g., 3.3.3).
   - Select your **Operating System**.
   - Select **Minimal** preset, but ensure **GLFW**, **OpenAL**, **OpenGL**, and **stb** are checked.
   - Download the ZIP and extract the JAR files into a folder named `lwjgl` in the project root.

## Compiling and Running
To compile the project:
```bash
javac -cp .:gson-2.10.1.jar:lwjgl/* *.java
```

To run with the standard Swing renderer:
```bash
java -cp .:gson-2.10.1.jar:lwjgl/* Main
```

To run with the hardware-accelerated LWJGL renderer:
```bash
java -cp .:gson-2.10.1.jar:lwjgl/* Main lwjgl
```

## Controls
- **Space**: Pause/Resume simulation.
- **T**: Toggle Slow Motion.
- **Ctrl+Z / Ctrl+Y**: Undo / Redo.
- **Ctrl+S / Ctrl+L**: Save / Load selected prefab.
- **Drag (DRAG mode)**: Box select entities.
- **Left/Right/R**: Rotate entity.
- **WASD**: Move player or control logic.