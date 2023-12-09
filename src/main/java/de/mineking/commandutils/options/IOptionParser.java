package de.mineking.commandutils.options;

import de.mineking.commandutils.Command;
import de.mineking.commandutils.CommandUtils;
import de.mineking.commandutils.annotation.Permission;
import de.mineking.commandutils.options.defaultValue.EnumDefault;
import dev.jorel.commandapi.arguments.*;
import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.regex.Matcher;

public interface IOptionParser {
	boolean accepts(@NotNull Class<?> type, @NotNull Type generic, @NotNull Parameter param);

	@NotNull
	Argument<?> build(@NotNull Class<?> type, @NotNull Type generic, @NotNull Parameter param, @NotNull Option info, @NotNull String name);

	@Nullable
	Object parse(@NotNull CommandArguments args, @NotNull String name, @NotNull Class<?> type, @NotNull Type generic, @NotNull Parameter param, @NotNull Option info);

	default void register(@NotNull Command cmd, @NotNull Type generic, @NotNull Parameter param, @NotNull Option info, @Nullable ArgumentSuggestions<CommandSender> autocomplete) {
		var option = build(param.getType(), generic, param, info, info.name().isEmpty() ? param.getName() : info.name())
				.setOptional(!info.required());

		if(autocomplete != null) option.includeSuggestions(autocomplete);
		if(param.isAnnotationPresent(Permission.class)) option.withPermission(param.getAnnotation(Permission.class).value());

		cmd.addOption(option);
	}

	IOptionParser INTEGER = new IOptionParser() {
		@Override
		public boolean accepts(@NotNull Class<?> type, @NotNull Type generic, @NotNull Parameter param) {
			return int.class.isAssignableFrom(type) || Integer.class.isAssignableFrom(type);
		}

		@Override
		public @NotNull Argument<Integer> build(@NotNull Class<?> type, @NotNull Type generic, @NotNull Parameter param, @NotNull Option info, @NotNull String name) {
			IntegerArgument arg;

			if(info.minValue() != Integer.MAX_VALUE && info.maxValue() != Integer.MAX_VALUE) arg = new IntegerArgument(name, (int) info.minValue(), (int) info.maxValue());
			else if(info.minValue() != Integer.MAX_VALUE) arg = new IntegerArgument(name, (int) info.minValue());
			else arg = new IntegerArgument(name);

			return arg;
		}

		@Override
		public @Nullable Integer parse(@NotNull CommandArguments args, @NotNull String name, @NotNull Class<?> type, @NotNull Type generic, @NotNull Parameter param, @NotNull Option info) {
			return (Integer) args.get(name);
		}
	};

	IOptionParser DOUBLE = new IOptionParser() {
		@Override
		public boolean accepts(@NotNull Class<?> type, @NotNull Type generic, @NotNull Parameter param) {
			return double.class.isAssignableFrom(type) || Double.class.isAssignableFrom(type);
		}

		@Override
		public @NotNull Argument<Double> build(@NotNull Class<?> type, @NotNull Type generic, @NotNull Parameter param, @NotNull Option info, @NotNull String name) {
			DoubleArgument arg;

			if(info.minValue() != Integer.MAX_VALUE && info.maxValue() != Integer.MAX_VALUE) arg = new DoubleArgument(name, info.minValue(), info.maxValue());
			else if(info.minValue() != Integer.MAX_VALUE) arg = new DoubleArgument(name, info.minValue());
			else arg = new DoubleArgument(name);

			return arg;
		}

		@Override
		public @Nullable Double parse(@NotNull CommandArguments args, @NotNull String name, @NotNull Class<?> type, @NotNull Type generic, @NotNull Parameter param, @NotNull Option info) {
			return (Double) args.get(name);
		}
	};

	IOptionParser BOOLEAN = new OptionParser(BooleanArgument::new, boolean.class, Boolean.class);

	IOptionParser STRING = new OptionParser(StringArgument::new, String.class);

	IOptionParser OFFLINE_PLAYER = new OptionParser(OfflinePlayerArgument::new, OfflinePlayer.class);

	IOptionParser PLAYER = new OptionParser(EntitySelectorArgument.OnePlayer::new, Player.class);

	IOptionParser PLAYER_LIST = new IOptionParser() {
		@Override
		public boolean accepts(@NotNull Class<?> type, @NotNull Type generic, @NotNull Parameter param) {
			return (type.isArray() && type.componentType() == Player.class) || (type.isAssignableFrom(List.class) && generic.equals(Player.class));
		}

		@Override
		public @NotNull Argument<?> build(@NotNull Class<?> type, @NotNull Type generic, @NotNull Parameter param, @NotNull Option info, @NotNull String name) {
			return new EntitySelectorArgument.ManyPlayers(name);
		}

		@Override
		public @Nullable Object parse(@NotNull CommandArguments args, @NotNull String name, @NotNull Class<?> type, @NotNull Type generic, @NotNull Parameter param, @NotNull Option info) {
			return args.get(name);
		}
	};


	IOptionParser OPTIONAL = new IOptionParser() {
		@Override
		public boolean accepts(@NotNull Class<?> type, @NotNull Type generic, @NotNull Parameter param) {
			return type.equals(Optional.class);
		}

		@Override
		public @NotNull Argument<?> build(@NotNull Class<?> type, @NotNull Type generic, @NotNull Parameter param, @NotNull Option info, @NotNull String name) {
			var p = ((ParameterizedType) generic).getActualTypeArguments()[0];
			return CommandUtils.INSTANCE.buildArgument((Class<?>) p, p, info, param, name);
		}

		@Override
		public @NotNull Optional<?> parse(@NotNull CommandArguments args, @NotNull String name, @NotNull Class<?> type, @NotNull Type generic, @NotNull Parameter param, @NotNull Option info) {
			var p = ((ParameterizedType) generic).getActualTypeArguments()[0];
			return Optional.ofNullable(CommandUtils.INSTANCE.parseArgument(args, name, (Class<?>) p, p, param, info));
		}
	};

	IOptionParser ENUM = new IOptionParser() {
		@Override
		public boolean accepts(@NotNull Class<?> type, @NotNull Type generic, @NotNull Parameter param) {
			return type.isEnum();
		}

		@Override
		public @NotNull Argument<String> build(@NotNull Class<?> type, @NotNull Type generic, @NotNull Parameter param, @NotNull Option info, @NotNull String name) {
			return new StringArgument(name).includeSuggestions(ArgumentSuggestions.strings(
					Arrays.stream((Enum<?>[]) type.getEnumConstants())
							.map(e -> {
								try {
									var f = type.getField(e.name()).getAnnotation(EnumConstant.class);
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
		public @Nullable Enum<?> parse(@NotNull CommandArguments args, @NotNull String name, @NotNull Class<?> type, @NotNull Type generic, @NotNull Parameter param, @NotNull Option info) {
			return getEnumConstant(type, (String) args.get(name)).orElseGet(() -> {
				var def = param.getAnnotation(EnumDefault.class);
				if(def == null) return null;

				return def.value().isEmpty() ? (Enum<?>) type.getEnumConstants()[0] : getEnumConstant(type, def.value()).orElse(null);
			});
		}

		private Optional<Enum<?>> getEnumConstant(Class<?> type, String name) {
			return Arrays.stream((Enum<?>[]) type.getEnumConstants())
					.filter(c -> c.name().equals(name))
					.findFirst();
		}
	};

	IOptionParser ARRAY = new IOptionParser() {
		@Override
		public boolean accepts(@NotNull Class<?> type, @NotNull Type generic, @NotNull Parameter param) {
			return type.isArray() || type.isAssignableFrom(List.class);
		}

		@Override
		public @NotNull Argument<?> build(@NotNull Class<?> type, @NotNull Type generic, @NotNull Parameter param, @NotNull Option info, @NotNull String name) {
			if(type.isArray()) return CommandUtils.INSTANCE.buildArgument(type.getComponentType(), generic, info, param, name);
			else {
				var p = ((ParameterizedType) generic).getActualTypeArguments()[0];
				return CommandUtils.INSTANCE.buildArgument((Class<?>) p, p, info, param, name);
			}
		}

		@Override
		public @Nullable Object parse(@NotNull CommandArguments args, @NotNull String name, @NotNull Class<?> type, @NotNull Type generic, @NotNull Parameter param, @NotNull Option info) {
			var result = new ArrayList<>();

			Class<?> ct;
			Type cg;

			if(type.isArray()) {
				ct = type.getComponentType();
				cg = generic;
			} else {
				var p = ((ParameterizedType) generic).getActualTypeArguments()[0];
				ct = (Class<?>) p;
				cg = p;
			}

			args.argsMap().forEach((n, value) -> {
				if(n.matches(Matcher.quoteReplacement(name) + "\\d+")) {
					result.add(CommandUtils.INSTANCE.parseArgument(args, n, ct, cg, param, info));
				}
			});

			return type.isArray() ? result.toArray(l -> (Object[]) Array.newInstance(type.getComponentType(), l)) : result;
		}

		@Override
		public void register(@NotNull Command cmd, @NotNull Type generic, @NotNull Parameter param, @NotNull Option info, @Nullable ArgumentSuggestions<CommandSender> autocomplete) {
			var oa = param.getAnnotation(OptionArray.class);

			var permission = param.isAnnotationPresent(Permission.class) ? param.getAnnotation(Permission.class).value() : null;

			if(oa == null) IOptionParser.super.register(cmd, generic, param, info, autocomplete);
			else {
				for(int i = 1; i <= oa.maxCount(); i++) {
					var o = build(param.getType(), generic, param, info, info.name().isEmpty() ? param.getName() : info.name());

					if(permission != null) o.withPermission(permission);
					if(autocomplete != null) o.includeSuggestions(autocomplete);

					if(i > oa.minCount()) o.setOptional(true);

					cmd.addOption(o);
				}
			}
		}
	};
}
