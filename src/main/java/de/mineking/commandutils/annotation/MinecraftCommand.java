package de.mineking.commandutils.annotation;

import dev.jorel.commandapi.executors.ExecutorType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MinecraftCommand {
	String name();

	String[] aliases() default {};

	ExecutorType[] executors() default {ExecutorType.PLAYER};
}
