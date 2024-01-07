package de.mineking.commandutils.options;

import de.mineking.commandutils.Command;
import de.mineking.commandutils.CommandUtils;
import de.mineking.commandutils.annotation.Permission;
import de.mineking.commandutils.options.defaultValue.EnumDefault;
import de.mineking.javautils.reflection.ReflectionUtils;
import dev.jorel.commandapi.arguments.*;
import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.*;
import java.util.regex.Matcher;

public interface IOptionParser {
	boolean accepts(@NotNull Type type, @NotNull Parameter param);

	@NotNull
	Argument<?> build(@NotNull Type type, @NotNull Parameter param, @NotNull Option info, @NotNull String name);

	@Nullable
	Object parse(@NotNull CommandArguments args, @NotNull String name, @NotNull Type type, @NotNull Parameter param, @NotNull Option info);

	default void register(@NotNull Command cmd, @NotNull Type generic, @NotNull Parameter param, @NotNull Option info, @Nullable ArgumentSuggestions<CommandSender> autocomplete) {
		var option = build(param.getType(), param, info, info.name().isEmpty() ? param.getName() : info.name())
				.setOptional(!info.required());

		if(autocomplete != null) option.includeSuggestions(autocomplete);
		if(param.isAnnotationPresent(Permission.class)) option.withPermission(param.getAnnotation(Permission.class).value());

		cmd.addOption(option);
	}

	IOptionParser INTEGER = new IOptionParser() {
		@Override
		public boolean accepts(@NotNull Type type, @NotNull Parameter param) {
			return type.equals(int.class) || type.equals(Integer.class);
		}

		@Override
		public @NotNull Argument<Integer> build(@NotNull Type type, @NotNull Parameter param, @NotNull Option info, @NotNull String name) {
			return new IntegerArgument(name, (int) info.minValue(), (int) info.maxValue());
		}

		@Override
		public @Nullable Integer parse(@NotNull CommandArguments args, @NotNull String name, @NotNull Type type, @NotNull Parameter param, @NotNull Option info) {
			return (Integer) args.get(name);
		}
	};

	IOptionParser DOUBLE = new IOptionParser() {
		@Override
		public boolean accepts(@NotNull Type type, @NotNull Parameter param) {
			return type.equals(double.class) || type.equals(Double.class);
		}

		@Override
		public @NotNull Argument<Double> build(@NotNull Type type, @NotNull Parameter param, @NotNull Option info, @NotNull String name) {
			return new DoubleArgument(name, info.minValue(), info.maxValue());
		}

		@Override
		public @Nullable Double parse(@NotNull CommandArguments args, @NotNull String name, @NotNull Type type, @NotNull Parameter param, @NotNull Option info) {
			return (Double) args.get(name);
		}
	};

	IOptionParser LONG = new IOptionParser() {
		@Override
		public boolean accepts(@NotNull Type type, @NotNull Parameter param) {
			return type.equals(long.class) || type.equals(Long.class);
		}

		@Override
		public @NotNull Argument<?> build(@NotNull Type type, @NotNull Parameter param, @NotNull Option info, @NotNull String name) {
			return new LongArgument(name, (long) info.minValue(), (long) info.maxValue());
		}

		@Override
		public @Nullable Long parse(@NotNull CommandArguments args, @NotNull String name, @NotNull Type type, @NotNull Parameter param, @NotNull Option info) {
			return (Long) args.get(name);
		}
	};

	IOptionParser BOOLEAN = new OptionParser(BooleanArgument::new, boolean.class, Boolean.class);

	IOptionParser STRING = new OptionParser(StringArgument::new, String.class);

	IOptionParser OFFLINE_PLAYER = new OptionParser(OfflinePlayerArgument::new, OfflinePlayer.class);

	IOptionParser PLAYER = new OptionParser(EntitySelectorArgument.OnePlayer::new, Player.class);

	IOptionParser PLAYER_LIST = new IOptionParser() {
		@Override
		public boolean accepts(@NotNull Type type, @NotNull Parameter param) {
			return ReflectionUtils.isArray(type, true) && ReflectionUtils.getComponentType(type).equals(Player.class);
		}

		@Override
		public @NotNull Argument<?> build(@NotNull Type type, @NotNull Parameter param, @NotNull Option info, @NotNull String name) {
			return new EntitySelectorArgument.ManyPlayers(name);
		}

		@Override
		public @Nullable Object parse(@NotNull CommandArguments args, @NotNull String name, @NotNull Type type, @NotNull Parameter param, @NotNull Option info) {
			var array = (Collection<Player>) args.get(name);

			if(array == null) return null;

			return ReflectionUtils.isArray(type, false)
					? array.toArray(i -> ReflectionUtils.createArray(Player.class, i))
					: createCollection(ReflectionUtils.getClass(type), array);
		}

		private <C> Collection<C> createCollection(Class<?> type, Collection<C> array) {
			if(type.isAssignableFrom(List.class)) return new ArrayList<>(array);
			else if(type.isAssignableFrom(Set.class)) return new HashSet<>(array);

			throw new IllegalStateException("Cannot create player collection for " + type.getTypeName());
		}
	};


	IOptionParser OPTIONAL = new IOptionParser() {
		@Override
		public boolean accepts(@NotNull Type type, @NotNull Parameter param) {
			return type.equals(Optional.class);
		}

		@Override
		public @NotNull Argument<?> build(@NotNull Type type, @NotNull Parameter param, @NotNull Option info, @NotNull String name) {
			return CommandUtils.INSTANCE.buildArgument(ReflectionUtils.getComponentType(type), info, param, name);
		}

		@Override
		public @NotNull Optional<?> parse(@NotNull CommandArguments args, @NotNull String name, @NotNull Type type, @NotNull Parameter param, @NotNull Option info) {
			return Optional.ofNullable(CommandUtils.INSTANCE.parseArgument(args, name, ReflectionUtils.getComponentType(type), param, info));
		}
	};

	IOptionParser ENUM = new IOptionParser() {
		@Override
		public boolean accepts(@NotNull Type type, @NotNull Parameter param) {
			return ReflectionUtils.getClass(type).isEnum();
		}

		@Override
		public @NotNull Argument<String> build(@NotNull Type type, @NotNull Parameter param, @NotNull Option info, @NotNull String name) {
			var clazz = ReflectionUtils.getClass(type);

			return new StringArgument(name).includeSuggestions(ArgumentSuggestions.strings(
					Arrays.stream((Enum<?>[]) clazz.getEnumConstants())
							.map(e -> {
								try {
									var f = clazz.getField(e.name()).getAnnotation(EnumConstant.class);
									if(f == null) return e.name();

									if(f.exclude()) return null;
									return f.display().isEmpty() ? e.name() : f.display();
								} catch(NoSuchFieldException ex) {
									throw new RuntimeException(ex);
								}
							})
							.filter(Objects::nonNull)
							.toList()
			));
		}

		@Override
		public @Nullable Enum<?> parse(@NotNull CommandArguments args, @NotNull String name, @NotNull Type type, @NotNull Parameter param, @NotNull Option info) {
			if(args.get(name) == null) return null;

			return ReflectionUtils.getEnumConstant(type, (String) args.get(name)).orElseGet(() -> {
				var def = param.getAnnotation(EnumDefault.class);
				if(def == null) return null;

				return def.value().isEmpty() ? (Enum<?>) ReflectionUtils.getClass(type).getEnumConstants()[0] : ReflectionUtils.getEnumConstant(type, def.value()).orElse(null);
			});
		}
	};

	IOptionParser ARRAY = new IOptionParser() {
		@Override
		public boolean accepts(@NotNull Type type, @NotNull Parameter param) {
			return ReflectionUtils.isArray(type, true);
		}

		@Override
		public @NotNull Argument<?> build(@NotNull Type type, @NotNull Parameter param, @NotNull Option info, @NotNull String name) {
			return CommandUtils.INSTANCE.buildArgument(ReflectionUtils.getComponentType(type), info, param, name);
		}

		@Override
		public @Nullable Object parse(@NotNull CommandArguments args, @NotNull String name, @NotNull Type type, @NotNull Parameter param, @NotNull Option info) {
			var component = ReflectionUtils.getComponentType(type);

			var array = args.argsMap().keySet().stream()
					.filter(o -> o.matches(Matcher.quoteReplacement(name) + "\\d+"))
					.map(o -> CommandUtils.INSTANCE.parseArgument(args, o, component, param, info))
					.toList();

			return ReflectionUtils.isArray(type, false)
					? array.toArray(i -> ReflectionUtils.createArray(component, i))
					: createCollection(ReflectionUtils.getClass(type), ReflectionUtils.getClass(component), array);
		}

		@SuppressWarnings("unchecked")
		private <C> Collection<C> createCollection(Class<?> type, Class<?> component, List<C> array) {
			if(type.isAssignableFrom(List.class)) return new ArrayList<>(array);
			else if(type.isAssignableFrom(Set.class)) return new HashSet<>(array);
			else if(type.isAssignableFrom(EnumSet.class)) return (Collection<C>) createEnumSet(array, component);

			throw new IllegalStateException("Cannot create collection for " + type.getTypeName() + " with component " + component.getTypeName());
		}

		@SuppressWarnings("unchecked")
		private <E extends Enum<E>> EnumSet<E> createEnumSet(Collection<?> collection, Class<?> component) {
			return collection.isEmpty() ? EnumSet.noneOf((Class<E>) component) : EnumSet.copyOf((Collection<E>) collection);
		}

		@Override
		public void register(@NotNull Command cmd, @NotNull Type type, @NotNull Parameter param, @NotNull Option info, @Nullable ArgumentSuggestions<CommandSender> autocomplete) {
			var oa = param.getAnnotation(OptionArray.class);

			var permission = param.isAnnotationPresent(Permission.class) ? param.getAnnotation(Permission.class).value() : null;

			if(oa == null) IOptionParser.super.register(cmd, type, param, info, autocomplete);
			else {
				for(int i = 1; i <= oa.maxCount(); i++) {
					var o = build(param.getType(), param, info, info.name().isEmpty() ? param.getName() : info.name());

					if(permission != null) o.withPermission(permission);
					if(autocomplete != null) o.includeSuggestions(autocomplete);

					if(i > oa.minCount()) o.setOptional(true);

					cmd.addOption(o);
				}
			}
		}
	};
}
