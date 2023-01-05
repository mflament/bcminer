package com.infine.demo.glbcminer;

import com.infine.demo.glbcminer.glsupport.GLContext;
import com.infine.demo.jbcminer.Bench;
import com.infine.demo.jbcminer.BlockHeader;
import com.infine.demo.jbcminer.BlockHeader.HashPredicate;
import com.infine.demo.jbcminer.java.Sha256;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static com.infine.demo.glbcminer.glsupport.GLSupport.check;
import static com.infine.demo.glbcminer.glsupport.GLSupport.loadProgram;
import static org.lwjgl.opengl.GL33.*;

public class GLMiner implements Bench.IMiner {

    public record GLMinerConfig(int textureSize, int reducedSize, int reduceFactor, boolean uvec4) {
    }

    private static final GLMinerConfig DEFAULT_CONFIG = new GLMinerConfig(8192,1,2,false);

    private final GLMinerConfig config;
    private final BlockHeader header;

    private long totalHashes;
    // uniform location
    private int uNonceLocation;

    private GLMiner(BlockHeader header, GLMinerConfig config) {
        this.config = config;
        this.header = header;
    }

    @Override
    public long getTotalHashes() {
        return totalHashes;
    }

    @Override
    public Integer mine(int startNonce) {
        GLContext glContext = new GLContext();
        glContext.makeCurrent();

        int hashProgram = createHashProgram(config.uvec4());
        int reduceProgram = createReduceProgram();

        createQuad();

        int textureSize = config.textureSize();
        int reducedSize = config.reducedSize();
        int reducedFactor = config.reduceFactor();
        RenderTarget resultsTarget = createRenderTarget(textureSize);
        RenderTarget reduceTarget;
        int bufferSize, resultsTextureSize;
        if (reducedFactor >= 2) {
            bufferSize = reducedSize * reducedSize;
            resultsTextureSize = reducedSize;
            reduceTarget = createRenderTarget(textureSize / reducedFactor);
        } else {
            bufferSize = textureSize * textureSize;
            resultsTextureSize = textureSize;
            reduceTarget = null;
        }
        IntBuffer resultsBuffer = BufferUtils.createIntBuffer(bufferSize);

        int passNonces = textureSize * textureSize;
        totalHashes = 0;
        long nonce = Integer.toUnsignedLong(startNonce);
        Integer matchedNonce = null;
        while (matchedNonce == null && nonce < 0xFFFFFFFFL) {
            // hash pass
            glViewport(0, 0, textureSize, textureSize);
            glUseProgram(hashProgram);
            glUniform1ui(uNonceLocation, (int) nonce);
            glBindFramebuffer(GL_FRAMEBUFFER, resultsTarget.framebuffer);
            glDrawArrays(GL_TRIANGLES, 0, 6);

            if (reduceTarget != null) {
                // reduce texture
                glUseProgram(reduceProgram);
                var src = resultsTarget;
                var target = reduceTarget;
                int size = textureSize;
                while (size > reducedSize) {
                    size = size / reducedFactor;
                    glViewport(0, 0, size, size);
                    glBindFramebuffer(GL_FRAMEBUFFER, target.framebuffer);
                    glBindTexture(GL_TEXTURE_2D, src.texture);
                    glDrawArrays(GL_TRIANGLES, 0, 6);
                    var swap = src;
                    src = target;
                    target = swap;
                }
            }

            // read from last bound frame buffer that contains the results
            glReadPixels(0, 0, resultsTextureSize, resultsTextureSize, GL_RED_INTEGER, GL_UNSIGNED_INT, resultsBuffer);
            for (int i = 0; i < bufferSize; i++) {
                int result = resultsBuffer.get(i);
                if (result != 0) {
                    matchedNonce = result;
                    break;
                }
            }
            totalHashes += passNonces;
            nonce += passNonces;
        }
        return matchedNonce;
    }

    private RenderTarget createRenderTarget(int size) {
        int texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R32UI, size, size, 0, GL_RED_INTEGER, GL_UNSIGNED_INT, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        int frameBuffer = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, frameBuffer);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        glDrawBuffers(GL_COLOR_ATTACHMENT0);
        glReadBuffer(GL_COLOR_ATTACHMENT0);
        return new RenderTarget(size, frameBuffer, texture);
    }

    private int createHashProgram(boolean uvec4) {
        String fs = uvec4 ? "hash-uvec4-fs.glsl" : "hash-uint-fs.glsl";
        // program
        int program = loadProgram("quad_vs.glsl", fs);
        glUseProgram(program);

        // program uniforms
        if (uvec4)
            createUvec4Uniforms(program);
        else
            createUintUniforms(program);

        uNonceLocation = glGetUniformLocation(program, "uNonce");
        return program;
    }

    private int createReduceProgram() {
        int program = loadProgram("quad_vs.glsl", "reduce-fs.glsl");
        glUseProgram(program);

        int loc = glGetUniformLocation(program, "uResults");
        glUniform1i(loc, 0);

        loc = glGetUniformLocation(program, "uReduceFactor");
        glUniform1i(loc, config.reduceFactor());

        return program;
    }

    private void createQuad() {
        // quad vao
        int vao = check(glGenVertexArrays());
        glBindVertexArray(vao);

        int vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, QUAD_VERTICES, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0);
    }

    /**
     * uvec4 :
     * uniform uint4 uData[5];
     * uniform uint4 uMidstate[2];
     * uniform ivec2  uHMaskOffset;
     * uniform uint uHMask;
     * uniform uint  uResultsSize;
     * uniform uint uNonce;
     */
    private void createUvec4Uniforms(int program) {
        int loc = glGetUniformLocation(program, "uData");
        glUniform4uiv(loc, header.data());

        int[] midstate = Sha256.createMidstate(header);
        loc = glGetUniformLocation(program, "uMidstate");
        glUniform4uiv(loc, midstate);

        HashPredicate hashPredicate = header.hashPredicate();
        int hMaskOffset = hashPredicate.hOffset();
        loc = glGetUniformLocation(program, "uHMaskOffset");
        glUniform2i(loc, hMaskOffset / 4, hMaskOffset % 4);

        loc = glGetUniformLocation(program, "uHMask");
        glUniform1ui(loc, hashPredicate.mask());

        loc = glGetUniformLocation(program, "uResultsSize");
        glUniform1ui(loc, config.textureSize());
    }

    /**
     * uniform uint uData[20];
     * uniform uint uMidstate[8];
     * uniform int  uHMaskOffset;
     * uniform uint uHMask;
     * uniform uint uResultsSize;
     * uniform uint uNonce;
     */
    private void createUintUniforms(int program) {
        int loc = glGetUniformLocation(program, "uData");
        glUniform1uiv(loc, header.data());

        int[] midstate = Sha256.createMidstate(header);
        loc = glGetUniformLocation(program, "uMidstate");
        glUniform1uiv(loc, midstate);

        HashPredicate hashPredicate = header.hashPredicate();
        int hMaskOffset = hashPredicate.hOffset();
        loc = glGetUniformLocation(program, "uHMaskOffset");
        glUniform1i(loc, hMaskOffset);

        loc = glGetUniformLocation(program, "uHMask");
        glUniform1ui(loc, hashPredicate.mask());

        loc = glGetUniformLocation(program, "uResultsSize");
        glUniform1ui(loc, config.textureSize());
    }

    public static void main(String[] args) throws InterruptedException {
        Bench.start(header -> new GLMiner(header, DEFAULT_CONFIG), 0);
    }

    private static final float[] QUAD_VERTICES = {
            // t0
            -1.0f, -1.0f,
            1.0f, -1.0f,
            1.0f, 1.0f,
            // t1
            1.0f, 1.0f,
            -1.0f, 1.0f,
            -1.0f, -1.0f,
    };

    record RenderTarget(int size, int framebuffer, int texture) {
    }
}

