package com.inza.twoh.client.render.fx;

import com.inza.twoh.AddonMain;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.IResource;
import net.minecraft.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.IOUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * EN: A render-thread program backed by two resource-pack files. Uniform locations belong to a single link.
 * 中文：由两个资源包文件构成的渲染线程程序。uniform 位置只在一次链接周期内有效。
 */
public final class ShaderProgram implements AutoCloseable {
    private static volatile long resourceVersion;
    private final ResourceLocation vertex;
    private final ResourceLocation fragment;
    private final Map<String, Integer> uniforms = new HashMap<>();
    private long loadedVersion = -1;
    private int program;

    public ShaderProgram(ResourceLocation vertex, ResourceLocation fragment) {
        this.vertex = vertex;
        this.fragment = fragment;
    }

    static void invalidateResources() {
        resourceVersion++;
    }

    public boolean bind() {
        long version = resourceVersion;
        if (loadedVersion != version) {
            loadedVersion = version;
            reload();
        }
        if (program == 0) {
            return false;
        }
        GL20.glUseProgram(program);
        return true;
    }

    public void setInt(String name, int value) {
        GL20.glUniform1i(location(name), value);
    }

    public void setFloat(String name, float value) {
        GL20.glUniform1f(location(name), value);
    }

    public void setVec3(String name, float x, float y, float z) {
        GL20.glUniform3f(location(name), x, y, z);
    }

    private int location(String name) {
        return uniforms.computeIfAbsent(name, key -> GL20.glGetUniformLocation(program, key));
    }

    private void reload() {
        IResourceManager resources = Minecraft.getInstance().getResourceManager();
        int vs = 0;
        int fs = 0;
        int candidate = 0;
        try {
            vs = compile(resources, GL20.GL_VERTEX_SHADER, vertex);
            if (vs == 0) {
                return;
            }
            fs = compile(resources, GL20.GL_FRAGMENT_SHADER, fragment);
            if (fs == 0) {
                return;
            }
            candidate = GL20.glCreateProgram();
            GL20.glAttachShader(candidate, vs);
            GL20.glAttachShader(candidate, fs);
            GL20.glLinkProgram(candidate);
            if (GL20.glGetProgrami(candidate, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                AddonMain.LOGGER.error("Failed to link {} + {}: {}", vertex, fragment, GL20.glGetProgramInfoLog(candidate));
                return;
            }
            // EN: Swap only a linked program; a typo during F3+T must not replace the last working material.
            // 中文：只提交链接成功的候选程序，F3+T 时的笔误不能替换仍然可用的材质。
            int old = program;
            program = candidate;
            candidate = 0;
            uniforms.clear();
            if (old != 0) {
                GL20.glDeleteProgram(old);
            }
        } catch (IOException failure) {
            AddonMain.LOGGER.error("Cannot load shader pair " + vertex + " + " + fragment, failure);
        } finally {
            if (vs != 0) {
                GL20.glDeleteShader(vs);
            }
            if (fs != 0) {
                GL20.glDeleteShader(fs);
            }
            if (candidate != 0) {
                GL20.glDeleteProgram(candidate);
            }
        }
    }

    private static int compile(IResourceManager resources, int type, ResourceLocation path) throws IOException {
        String source;
        try (IResource resource = resources.getResource(path)) {
            source = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
        }
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) != GL11.GL_FALSE) {
            return shader;
        }
        AddonMain.LOGGER.error("Failed to compile {}: {}", path, GL20.glGetShaderInfoLog(shader));
        GL20.glDeleteShader(shader);
        return 0;
    }

    @Override
    public void close() {
        if (program != 0) {
            GL20.glDeleteProgram(program);
            program = 0;
        }
        uniforms.clear();
        loadedVersion = -1;
    }
}
