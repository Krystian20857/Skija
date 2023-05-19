package io.github.humbleui.skija;

import io.github.humbleui.skija.impl.Managed;
import org.jetbrains.annotations.ApiStatus;

public class RecordingContext extends Managed {

    public RecordingContext(long ptr) {
        super(ptr, 0, false);
    }

}
