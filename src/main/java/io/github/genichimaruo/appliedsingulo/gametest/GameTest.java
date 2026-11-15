package io.github.genichimaruo.appliedsingulo.gametest;
import java.lang.annotation.*;
/** Local declaration retained while registering tests through the 26.1 registry API. */
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
public @interface GameTest { String template() default "empty"; int timeoutTicks() default 100; }
