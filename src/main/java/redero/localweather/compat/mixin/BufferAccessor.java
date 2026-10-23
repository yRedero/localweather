package redero.localweather.compat.mixin;

import java.nio.FloatBuffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.qendolin.betterclouds.clouds.Buffer;

@Mixin(value = Buffer.class, remap = false)
public interface BufferAccessor {
    @Accessor("writeBuffer")
    FloatBuffer localweather$writeBuffer();
}
