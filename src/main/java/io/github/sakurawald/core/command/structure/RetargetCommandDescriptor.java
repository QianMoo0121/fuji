package io.github.sakurawald.core.command.structure;

import com.mojang.brigadier.Command;
import io.github.sakurawald.core.auxiliary.LogUtil;
import io.github.sakurawald.core.auxiliary.minecraft.CommandHelper;
import io.github.sakurawald.core.command.argument.structure.Argument;
import io.github.sakurawald.core.command.argument.wrapper.impl.PlayerCollection;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RetargetCommandDescriptor extends CommandDescriptor {

    private static final int COMMAND_TARGET_DUMMY_PARAMETER_INDEX = 1024;

    private final int commandTargetArgumentIndex;

    private RetargetCommandDescriptor(Method method, List<Argument> arguments, int commandTargetArgumentIndex) {
        super(method, arguments);
        this.commandTargetArgumentIndex = commandTargetArgumentIndex;
    }

    private static Optional<Integer> findCommandTargetArgumentIndex(CommandDescriptor descriptor) {
        for (int i = 0; i < descriptor.arguments.size(); i++) {
            Argument argument = descriptor.arguments.get(i);
            if (argument.isCommandTarget()) {
                return Optional.of(i);
            }
        }

        return Optional.empty();
    }

    public static Optional<RetargetCommandDescriptor> make(CommandDescriptor commandDescriptor) {
        /* filter: the method that contains @CommandTarget */
        Optional<Integer> commandTargetArgumentIndexOpt = findCommandTargetArgumentIndex(commandDescriptor);
        if (commandTargetArgumentIndexOpt.isEmpty()) {
            return Optional.empty();
        }
        int commandTargetArgumentIndex = commandTargetArgumentIndexOpt.get();

        /* make retarget command descriptor */
        List<Argument> transformedArgs = transformWithOthersArguments(commandDescriptor.arguments);

        RetargetCommandDescriptor retargetCommandDescriptor = new RetargetCommandDescriptor(commandDescriptor.method, transformedArgs, commandTargetArgumentIndex);
        return Optional.of(retargetCommandDescriptor);
    }


    private static List<Argument> transformWithOthersArguments(List<Argument> arguments) {
        List<Argument> ret = new ArrayList<>(arguments
            .stream()
            .filter(it ->
                /*
                 remove the argument that is annotated with @CommandTarget and is not annotated with @CommandSource,
                 so that this argument will not be registered in the command tree.
                 Consider `/fly{4} others{4} <others>(){4} <player>(ST){4}`
                 */
                it.isCommandSource() || !it.isCommandTarget()
            )
            .toList());

        for (int argumentIndex = 0; argumentIndex < arguments.size(); argumentIndex++) {
            Argument argument = arguments.get(argumentIndex);

            /* ensure the `others` args are the `first required argument`, so that the `makeCommandFunctionArgs()` can extract the targets in the first arg */
            if (argument.isRequiredArgument() || argumentIndex == ret.size() - 1) {

                /* all retarget commands require level 4 permission to use */
                CommandRequirementDescriptor requirement = new CommandRequirementDescriptor(4, null);

                ret.add(argumentIndex, Argument.makeLiteralArgument("others", requirement));
                ret.add(argumentIndex + 1, Argument.makeRequiredArgument(PlayerCollection.class, "others", false, requirement));
                break;
            }

        }

        return ret;
    }

    @Override
    protected Command<ServerCommandSource> makeCommandFunctionClosure() {
        return (ctx) -> {

            /* verify command source */
            if (!verifyCommandSource(ctx, this)) {
                return CommandHelper.Return.FAIL;
            }

            LogUtil.debug("execute retarget command: initialing command source = {}", ctx.getSource().getName());

            /* invoke the command function */
            List<Object> args = makeCommandFunctionArgs(ctx);

            /* apply the command execution for each target. */
            PlayerCollection targets = (PlayerCollection) args.getFirst();
            LogUtil.debug("get the targets argument (the first argument in args): {}", targets.getValue().stream().map(it -> it.getGameProfile().getName()).toList());

            int finalValue = CommandHelper.Return.SUCCESS;
            for (ServerPlayerEntity target : targets.getValue()) {
                List<Object> unboxedArgs = args.subList(1, args.size());
                /*
                 if the @CommandSource and @CommandTarget are both annotated in the same parameter:
                 1. The @CommandSource will still be used to verify the type of `initialing command source`.
                 2. After that, the command source passed to the command method will be overridden by the @CommandTarget.
                 3. Any exceptions thrown during the execution of the command method, will be reported to the `initialing command source`.
                 */
                if (this.commandTargetArgumentIndex < unboxedArgs.size()) {
                    unboxedArgs.set(this.commandTargetArgumentIndex, target);
                } else {
                    // if the commandTargetAnnotationIndex < unboxedArgs, then it means the argument annotated with @CommandTarget is filtered.
                    unboxedArgs.add(this.commandTargetArgumentIndex, target);
                }

                LogUtil.debug("invoke command method {} in class {}: target = {}, args = {}"
                    , this.method.getName()
                    , this.method.getDeclaringClass().getSimpleName()
                    , target.getGameProfile().getName()
                    , unboxedArgs);

                try {
                    // if one of the execution if failed, then it's considered the whole return value is failed.
                    int singleValue = (int) this.method.invoke(null, unboxedArgs.toArray());
                    LogUtil.debug("the return value of command method is {}: target = {}, args = {}"
                        , singleValue
                        , target.getGameProfile().getName()
                        , unboxedArgs);

                    if (singleValue != CommandHelper.Return.SUCCESS) {
                        finalValue = CommandHelper.Return.FAIL;
                    }

                } catch (Exception wrappedOrUnwrappedException) {
                    return handleException(ctx, this.method, wrappedOrUnwrappedException);
                }
            }

            return finalValue;
        };
    }
}
