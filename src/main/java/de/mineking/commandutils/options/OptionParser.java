package de.mineking.commandutils.options;

import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.executors.CommandArguments;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class OptionParser implements IOptionParser {
	private final Function<String, Argument<?>> constructor;
	private final List<Class<?>> types;

	public OptionParser(Function<String, Argument<?>> constructor, Class<?>... types) {
		this.constructor = constructor;
		this.types = Arrays.asList(types);
	}

	@Override
	public boolean accepts(@NotNull Type type, @NotNull Parameter param) {
		return types.stream().anyMatch(c -> c.isAssignableFrom(type));
	}

	@Override
	public @NotNull Argument<?> build(@NotNull Type type, @NotNull Parameter param, @NotNull Option info, @NotNull String name) {
		return constructor.apply(name);
	}

	@Override
	public @Nullable Object parse(@NotNull CommandArguments args, @NotNull String name, @NotNull Type type, @NotNull Parameter param, @NotNull Option info) {
		return args.get(name);
	}
}
