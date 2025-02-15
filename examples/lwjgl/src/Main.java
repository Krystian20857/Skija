package io.github.humbleui.skija.examples.lwjgl;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.*;

import io.github.humbleui.skija.*;
import io.github.humbleui.skija.examples.scenes.*;
import io.github.humbleui.skija.impl.*;
import io.github.humbleui.types.*;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String [] args) throws Exception {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit())
            throw new IllegalStateException("Unable to initialize GLFW");

        GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        int width = (int) (vidmode.width() * 0.75);
        int height = (int) (vidmode.height() * 0.75);
        IRect bounds = IRect.makeXYWH(
                         Math.max(0, (vidmode.width() - width) / 2),
                         Math.max(0, (vidmode.height() - height) / 2),
                         width,
                         height);
        new Window().run(bounds);
    }
}

class Window {
    public long window;
    public int width;
    public int height;
    public float dpi = 1f;
    public int xpos = 0;
    public int ypos = 0;
    public boolean vsync = true;
    public boolean stats = true;
    public boolean renderTexture = false;
    public BackendTexture texture;
    private int[] refreshRates;
    private String os = System.getProperty("os.name").toLowerCase();

    private int[] getRefreshRates() {
        var monitors = glfwGetMonitors();
        int[] res = new int[monitors.capacity()];
        for (int i=0; i < monitors.capacity(); ++i) {
            res[i] = glfwGetVideoMode(monitors.get(i)).refreshRate();
        }
        return res;
    }

    public void run(IRect bounds) {
        refreshRates = getRefreshRates();

        createWindow(bounds);
        loop();

        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    private void updateDimensions() {
        int[] width = new int[1];
        int[] height = new int[1];
        glfwGetFramebufferSize(window, width, height);

        float[] xscale = new float[1];
        float[] yscale = new float[1];
        glfwGetWindowContentScale(window, xscale, yscale);
        assert xscale[0] == yscale[0] : "Horizontal dpi=" + xscale[0] + ", vertical dpi=" + yscale[0];

        this.width = (int) (width[0] / xscale[0]);
        this.height = (int) (height[0] / yscale[0]);
        this.dpi = xscale[0];
        System.out.println("FramebufferSize " + width[0] + "x" + height[0] + ", scale " + this.dpi + ", window " + this.width + "x" + this.height);
    }

    private void createWindow(IRect bounds) {
        glfwDefaultWindowHints(); // optional, the current window hints are already the default
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // the window will stay hidden after creation
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE); // the window will be resizable

        window = glfwCreateWindow(bounds.getWidth(), bounds.getHeight(), "Skija LWJGL Demo", NULL, NULL);
        if (window == NULL)
            throw new RuntimeException("Failed to create the GLFW window");

        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE)
                glfwSetWindowShouldClose(window, true);
        });

        glfwSetWindowPos(window, bounds.getLeft(), bounds.getTop());
        updateDimensions();
        xpos = width / 2;
        ypos = height / 2;

        glfwMakeContextCurrent(window);
        glfwSwapInterval(vsync ? 1 : 0); // Enable v-sync
        glfwShowWindow(window);
    }

    private DirectContext context;
    private BackendRenderTarget renderTarget;
    private Surface surface;
    private Canvas canvas;

    private void initSkia() {
        Stats.enabled = true;

        if (surface != null)
            surface.close();
        if (renderTarget != null)
            renderTarget.close();

        renderTarget = BackendRenderTarget.makeGL(
                         (int) (width * dpi),
                         (int) (height * dpi),
                         /*samples*/0,
                         /*stencil*/8,
                         /*fbId*/0,
                         FramebufferFormat.GR_GL_RGBA8);

        surface = Surface.makeFromBackendRenderTarget(
                    context,
                    renderTarget,
                    SurfaceOrigin.BOTTOM_LEFT,
                    SurfaceColorFormat.RGBA_8888,
                    ColorSpace.getDisplayP3(),  // TODO load monitor profile
                    new SurfaceProps(PixelGeometry.RGB_H));

        canvas = surface.getCanvas();
    }

    private void draw() {
        if (this.renderTexture) {
            if (this.texture == null) {
                try {
                    final var textureId = GL11.glGenTextures();
                    GL15.glActiveTexture(GL15.GL_TEXTURE0);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
                    final var textureBytes = Files.readAllBytes(Paths.get(Scene.file("images/whytouserust.png")));
                    final var buffer = memAlloc(textureBytes.length);
                    buffer.put(textureBytes);
                    buffer.rewind();

                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        IntBuffer width = stack.mallocInt(1);
                        IntBuffer height = stack.mallocInt(1);
                        IntBuffer comp = stack.mallocInt(1);

                        ByteBuffer image = STBImage.stbi_load_from_memory(buffer, width, height, comp, 4);
                        final var texWidth = width.get();
                        final var texHeight = height.get();
                        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, texWidth, texHeight, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, image);
                        STBImage.stbi_image_free(image);
                        this.texture = BackendTexture.makeGL(texWidth, texHeight, false, GL11.GL_TEXTURE_2D, textureId, GL11.GL_RGBA8);
                    }

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            final var recordingContext = this.canvas.getContext();
            try (final var image = Image.makeFromTexture(recordingContext, this.texture, SurfaceOrigin.TOP_LEFT, ColorType.RGBA_8888, ColorAlphaType.PREMUL, ColorSpace.getDisplayP3())) {
                canvas.clear(0xFFFFFFFF);
                final var save = canvas.save();
                canvas.scale(0.5f, 0.5f);
                canvas.drawImage(image, 0, 0);
                canvas.restoreToCount(save);
            }
        } else {
            Scenes.draw(canvas, width, height, dpi, xpos, ypos);
        }
        context.flush();
        glfwSwapBuffers(window);
    }

    private void loop() {
        GL.createCapabilities();
        if ("false".equals(System.getProperty("skija.staticLoad")))
            Library.load();
        context = DirectContext.makeGL();

        GLFW.glfwSetWindowSizeCallback(window, (window, width, height) -> {
            updateDimensions();
            initSkia();
            draw();
        });

        glfwSetCursorPosCallback(window, (window, xpos, ypos) -> {
            if(os.contains("mac") || os.contains("darwin")) {
                this.xpos = (int) xpos;
                this.ypos = (int) ypos;
            } else {
                this.xpos = (int) (xpos / dpi);
                this.ypos = (int) (ypos / dpi);
            }
        });

        glfwSetMouseButtonCallback(window, (window, button, action, mods) -> {
            // System.out.println("Button " + button + " " + (action == 0 ? "released" : "pressed"));
        });

        glfwSetScrollCallback(window, (window, xoffset, yoffset) -> {
            Scenes.currentScene().onScroll((float) xoffset * dpi, (float) yoffset * dpi);
        });

        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if (action == GLFW_PRESS) {
                switch (key) {
                    case GLFW_KEY_LEFT:
                        Scenes.prevScene();
                        break; 
                    case GLFW_KEY_RIGHT:
                        Scenes.nextScene();
                        break;
                    case GLFW_KEY_UP:
                        Scenes.currentScene().changeVariant(-1);
                        break;
                    case GLFW_KEY_DOWN:
                        Scenes.currentScene().changeVariant(1);
                        break;
                    case GLFW_KEY_V:
                        vsync = !vsync;
                        glfwSwapInterval(vsync ? 1 : 0);
                        HUD.extras.set(0, new Pair("V", "VSync: " + (vsync ? "ON" : "OFF")));
                        break;
                    case GLFW_KEY_S:
                        Scenes.stats = !Scenes.stats;
                        Stats.enabled = Scenes.stats;
                        break;
                    case GLFW_KEY_G:
                        System.out.println("Before GC " + Stats.allocated);
                        System.gc();
                        break;
                    case GLFW_KEY_T:
                        renderTexture = !renderTexture;
                        break;
                }
            }
        });

        HUD.extras.add(new Pair("V", "VSync: " + (vsync ? "ON" : "OFF")));
        initSkia();

        while (!glfwWindowShouldClose(window)) {
            draw();
            glfwPollEvents();
        }
    }
}
