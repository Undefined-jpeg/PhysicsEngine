import org.lwjgl.openal.*;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

public class AudioEngine {
    private long device;
    private long context;
    private Map<String, Integer> soundBuffers = new HashMap<>();
    private int[] sources = new int[16];
    private int sourceIndex = 0;

    public AudioEngine() {
        device = alcOpenDevice((ByteBuffer)null);
        if (device == NULL) {
            throw new IllegalStateException("Failed to open the default OpenAL device.");
        }
        ALCCapabilities deviceCaps = ALC.createCapabilities(device);
        context = alcCreateContext(device, (IntBuffer)null);
        if (context == NULL) {
            throw new IllegalStateException("Failed to create OpenAL context.");
        }
        alcMakeContextCurrent(context);
        AL.createCapabilities(deviceCaps);

        for (int i = 0; i < sources.length; i++) {
            sources[i] = alGenSources();
        }
    }

    public void loadSound(String name, String path) {
        try (MemoryStack stack = stackPush()) {
            IntBuffer channels = stack.mallocInt(1);
            IntBuffer sampleRate = stack.mallocInt(1);
            ShortBuffer rawAudio = STBVorbis.stb_vorbis_decode_filename(path, channels, sampleRate);
            if (rawAudio == null) {
                System.err.println("Failed to load sound: " + path);
                return;
            }

            int buffer = alGenBuffers();
            int format = channels.get(0) == 1 ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16;
            alBufferData(buffer, format, rawAudio, sampleRate.get(0));
            soundBuffers.put(name, buffer);
        } catch (Exception e) {
            System.err.println("Error loading sound " + path + ": " + e.getMessage());
        }
    }

    public void playSound(String name, float volume, float pitch) {
        if (!soundBuffers.containsKey(name)) return;
        int source = sources[sourceIndex];
        sourceIndex = (sourceIndex + 1) % sources.length;

        alSourceStop(source);
        alSourcei(source, AL_BUFFER, soundBuffers.get(name));
        alSourcef(source, AL_GAIN, volume);
        alSourcef(source, AL_PITCH, pitch);
        alSourcePlay(source);
    }

    public void cleanup() {
        for (int source : sources) alDeleteSources(source);
        for (int buffer : soundBuffers.values()) alDeleteBuffers(buffer);
        alcMakeContextCurrent(NULL);
        alcDestroyContext(context);
        alcCloseDevice(device);
    }
}
