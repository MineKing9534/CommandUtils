package de.mineking.commandutils;

import de.mineking.commandutils.annotation.CommandMethod;
import de.mineking.commandutils.annotation.MinecraftCommand;
import de.mineking.commandutils.options.Autocomplete;
import de.mineking.commandutils.options.Option;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

public class AnnotatedCommand extends Command {
	public final Class<?> type;

	private final BiFunction<CommandSender, CommandArguments, Object> instance;
	private final Method method;

	@SuppressWarnings("unchecked")
	private AnnotatedCommand(Class<?> type, MinecraftCommand info, BiFunction<CommandSender, CommandArguments, Object> instance) {
		super(info.name(), info.aliases());

		this.instance = instance;
		this.type = type;
		this.executors = Set.of(info.executors());

		Method method = null;

		for(var m : type.getMethods()) {
			if(m.isAnnotationPresent(CommandMethod.class)) {
				method = m;
				break;
			}
		}

		this.method = method;

		if(method != null) {
			var autocomplete = new HashMap<String, ArgumentSuggestions<CommandSender>>();

			for(var m : type.getMethods()) {
				var a = m.getAnnotation(Autocomplete.class);
				if(a == null) continue;

				autocomplete.put(a.value(), ArgumentSuggestions.stringCollectionAsync(ai -> CompletableFuture.supplyAsync(() -> {
					var params = new Object[m.getParameterCount()];

					for(int i = 0; i < m.getParameterCount(); i++) {
						var p = m.getParameters()[i];

						if(p.getType().isAssignableFrom(ai.sender().getClass())) params[i] = ai.sender();
						else if(p.getType().isAssignableFrom(CommandArguments.class)) params[i] = ai.previousArgs();
						else if(p.getType().isAssignableFrom(String.class)) params[i] = ai.currentArg();
					}

					try {
						return (Collection<String>) m.invoke(instance.apply(ai.sender(), ai.previousArgs()), params);
					} catch(IllegalAccessException | InvocationTargetException e) {
						CommandUtils.INSTANCE.getSLF4JLogger().error("Failed to invoke autocomplete method", e instanceof InvocationTargetException ie ? ie.getCause() : e);
						return Collections.emptyList();
					}
				})));
			}

			var generics = method.getGenericParameterTypes();
			var params = method.getParameters();

			for(int i = 0; i < method.getParameterCount(); i++) {
				var p = params[i];
				var g = generics[i];

				var option = p.getAnnotation(Option.class);
				if(option == null) continue;

				CommandUtils.INSTANCE.findParser(p.getType(), g, p).register(this, g, p, option, autocomplete.get(p.getName()));
			}
		}

		for(var c : type.getClasses()) {
			if(!c.isAnnotationPresent(MinecraftCommand.class)) continue;

			var i = CommandUtils.INSTANCE.createInstance(c);
			addSubcommand(get(type, (s, a) -> i));
		}
	}

	public static AnnotatedCommand get(@NotNull Class<?> type, @NotNull BiFunction<CommandSender, CommandArguments, Object> instance) {
		var info = type.getAnnotation(MinecraftCommand.class);
		if(info == null) throw new IllegalArgumentException();
		return new AnnotatedCommand(type, info, instance);
	}

	@Override
	public void perform(@NotNull CommandSender sender, @NotNull CommandArguments args) throws Throwable {
		if(method == null) return;

		var params = new Object[method.getParameterCount()];

		for(int i = 0; i < method.getParameterCount(); i++) {
			var p = method.getParameters()[i];
			var g = method.getGenericParameterTypes()[i];

			var option = p.getAnnotation(Option.class);

			if(option == null) {
				if(p.getType().isAssignableFrom(sender.getClass())) params[i] = sender;
			} else params[i] = CommandUtils.INSTANCE.parseArgument(args, option.name().isEmpty() ? p.getName() : option.name(), p.getType(), g, p, option);
		}

		try {
			method.invoke(instance.apply(sender, args), params);
		} catch(InvocationTargetException e) {
			throw e.getCause();
		}
	}
}
