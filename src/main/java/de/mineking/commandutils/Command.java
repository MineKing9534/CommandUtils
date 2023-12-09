package de.mineking.commandutils;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.executors.CommandArguments;
import dev.jorel.commandapi.executors.ExecutorType;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public abstract class Command {
	private final String name;

	private final List<Argument<?>> options = new ArrayList<>();
	private final Set<Command> subcommands = new HashSet<>();

	private final Set<String> aliases = new HashSet<>();

	protected Set<ExecutorType> executors = new HashSet<>(List.of(ExecutorType.PLAYER, ExecutorType.BLOCK, ExecutorType.CONSOLE));

	protected String permission;

	public Command(@NotNull String name, @NotNull String... aliases) {
		this.name = name;
		this.aliases.addAll(Arrays.asList(aliases));
	}

	public void performPlayer(@NotNull Player sender, @NotNull CommandArguments args) throws Throwable {}

	public void performBlock(@NotNull BlockCommandSender sender, @NotNull CommandArguments args) throws Throwable {}

	public void performConsole(@NotNull ConsoleCommandSender sender, @NotNull CommandArguments args) throws Throwable {}

	public void perform(@NotNull CommandSender sender, @NotNull CommandArguments args) throws Throwable {}

	@NotNull
	public String getName() {
		return name;
	}

	@NotNull
	public final Command addSubcommand(@NotNull Command command) {
		if(!options.isEmpty()) throw new IllegalStateException();

		subcommands.add(command);
		return this;
	}

	public final Command addSubcommand(@NotNull Class<?> type) {
		if(!options.isEmpty()) throw new IllegalStateException();
		var instance = CommandUtils.INSTANCE.createInstance(type);
		return addSubcommand(AnnotatedCommand.get(type, (s, a) -> instance));
	}

	@NotNull
	public final Command addOption(@NotNull Argument<?> option) {
		if(!subcommands.isEmpty()) throw new IllegalStateException();

		options.add(option);
		return this;
	}

	public CommandAPICommand build() {
		var temp = new CommandAPICommand(name)
				.withSubcommands(subcommands.stream()
						.map(Command::build)
						.toArray(CommandAPICommand[]::new)
				)
				.withAliases(aliases.toArray(String[]::new))
				.withArguments(options)
				.executes((sender, args) -> {
					try {
						perform(sender, args);
					} catch(Throwable e) {
						CommandUtils.INSTANCE.getSLF4JLogger().error("Error execution general method", e);
					}
				}, executors.toArray(ExecutorType[]::new));

		if(permission != null) temp.withPermission(permission);

		if(executors.contains(ExecutorType.PLAYER)) temp.executesPlayer((sender, args) -> {
			try {
				performPlayer(sender, args);
			} catch(Throwable e) {
				CommandUtils.INSTANCE.getSLF4JLogger().error("Error execution player method", e);
			}
		});
		if(executors.contains(ExecutorType.CONSOLE)) temp.executesConsole((sender, args) -> {
			try {
				performConsole(sender, args);
			} catch(Throwable e) {
				CommandUtils.INSTANCE.getSLF4JLogger().error("Error execution console method", e);
			}
		});
		if(executors.contains(ExecutorType.BLOCK)) temp.executesCommandBlock((sender, args) -> {
			try {
				performBlock(sender, args);
			} catch(Throwable e) {
				CommandUtils.INSTANCE.getSLF4JLogger().error("Error execution block method", e);
			}
		});

		return temp;
	}
}
