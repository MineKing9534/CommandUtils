package de.mineking.commandutils;

import de.mineking.commandutils.options.IOptionParser;
import de.mineking.commandutils.options.Option;
import de.mineking.commandutils.options.OptionParser;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

public final class CommandUtils extends JavaPlugin {
	public static CommandUtils INSTANCE;

	private final List<IOptionParser> parsers = new ArrayList<>();
	private final Set<Command> commands = new HashSet<>();

	@Override
	public void onEnable() {
		INSTANCE = this;

		parsers.add(IOptionParser.INTEGER);
		parsers.add(IOptionParser.DOUBLE);
		parsers.add(IOptionParser.STRING);
		parsers.add(IOptionParser.BOOLEAN);
		parsers.add(IOptionParser.OFFLINE_PLAYER);
		parsers.add(IOptionParser.PLAYER);
		parsers.add(IOptionParser.PLAYER_LIST);
		parsers.add(IOptionParser.ENUM);
		parsers.add(IOptionParser.OPTIONAL);
		parsers.add(IOptionParser.ARRAY);
	}

	@NotNull
	public CommandUtils registerOptionParser(@NotNull OptionParser parser) {
		parsers.add(0, parser);
		return this;
	}

	@NotNull
	public CommandUtils registerCommand(@NotNull Command command) {
		commands.add(command);
		command.build().register();
		return this;
	}

	@NotNull
	public Command findCommand(@NotNull String name) {
		return commands.stream()
				.filter(c -> c.getName().equals(name))
				.findFirst().orElseThrow();
	}

	@NotNull
	public CommandUtils registerCommand(@NotNull Class<?> type, @NotNull BiFunction<CommandSender, CommandArguments, Object> instance) {
		return registerCommand(AnnotatedCommand.get(type, instance));
	}

	@NotNull
	public CommandUtils registerCommand(@NotNull Class<?> type) {
		var instance = createInstance(type);
		return registerCommand(type, (s, a) -> instance);
	}

	@NotNull
	public IOptionParser findParser(@NotNull Class<?> type, @NotNull Type generic, @NotNull Parameter param) {
		return parsers.stream()
				.filter(p -> p.accepts(type, generic, param))
				.findFirst().orElseThrow();
	}

	@NotNull
	public Argument<?> buildArgument(@NotNull Class<?> type, @NotNull Type generic, @NotNull Option option, @NotNull Parameter param, @NotNull String name) {
		return findParser(type, generic, param).build(type, generic, param, option, name);
	}

	@Nullable
	public Object parseArgument(@NotNull CommandArguments args, @NotNull String name, @NotNull Class<?> type, @NotNull Type generic, @NotNull Parameter param, @NotNull Option info) {
		return findParser(type, generic, param).parse(args, name, type, generic, param, info);
	}

	@NotNull
	public Object createInstance(@NotNull Class<?> type) {
		var c = type.getConstructors()[0];

		try {
			var params = new Object[c.getParameterCount()];

			for(int i = 0; i < c.getParameterCount(); i++) {
				var p = c.getParameters()[i];

			}

			return c.newInstance(params);
		} catch(Exception e) {
			getSLF4JLogger().error("Error creating command instance", e);
			return null;
		}
	}
}
