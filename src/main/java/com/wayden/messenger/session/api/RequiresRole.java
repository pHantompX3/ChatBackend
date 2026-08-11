package com.wayden.messenger.session.api;

import com.wayden.messenger.identity.domain.SystemRole;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRole {
  SystemRole[] value() default {SystemRole.USER};
}
