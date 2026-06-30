package com.mtxgdn.common.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CommandRegistry {

    private static final Map<String, Command> commands = new LinkedHashMap<>();

    private CommandRegistry() {
    }

    public static void register(Command command) {
        for (String name : command.getNames()) {
            commands.put(name.toLowerCase(), command);
        }
    }

    /** 取消注册一个命令（通过命令实体的所有名称移除）。 */
    public static void unregister(Command command) {
        if (command == null) return;
        for (String name : command.getNames()) {
            commands.remove(name.toLowerCase(), command);
        }
    }

    public static Command get(String name) {
        return commands.get(name.toLowerCase());
    }

    public static List<Command> getAllUnique() {
        List<Command> unique = new ArrayList<>();
        for (Command cmd : commands.values()) {
            if (!unique.contains(cmd)) {
                unique.add(cmd);
            }
        }
        return unique;
    }

    public static int count() {
        return getAllUnique().size();
    }

    public static void clear() {
        commands.clear();
    }
}
