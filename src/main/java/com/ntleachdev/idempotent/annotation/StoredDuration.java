package com.ntleachdev.idempotent.annotation;

import com.ntleachdev.idempotent.core.TimeScale;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface StoredDuration {
    int value();
    TimeScale scale();
}
