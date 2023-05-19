package io.github.humbleui.skija;

import io.github.humbleui.skija.impl.Managed;
import org.jetbrains.annotations.ApiStatus;

public class BackendTexture extends Managed {

    public BackendTexture(long ptr) {
        super(ptr, _FinalizerHolder.PTR);
    }

    public static BackendTexture makeGL(int width, int height, boolean mipmapped, int target, int id, int format) {
        final long ptr = _nMakeGL(width, height, mipmapped, target, id, format);
        if (ptr == 0)
            throw new RuntimeException("Failed to makeGL");
        return new BackendTexture(ptr);
    }

    @ApiStatus.Internal
    public static class _FinalizerHolder {
        public static final long PTR = _nGetFinalizer();
    }

    @ApiStatus.Internal public static native long _nGetFinalizer();
    @ApiStatus.Internal public static native long _nMakeGL(int width, int height, boolean mipmapped, int target, int id, int format);
}
