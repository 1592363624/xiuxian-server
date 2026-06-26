package com.mtxgdn.minecraft.adapter;

/**
 * MC 指令处理结果，供 {@link MinecraftAdapter#handleMcCommand} 返回。
 */
public class McCommandResult {

    private final boolean ok;
    private final String text;

    private McCommandResult(boolean ok, String text) {
        this.ok = ok;
        this.text = text;
    }

    public static McCommandResult ok(String text) {
        return new McCommandResult(true, text);
    }

    public static McCommandResult error(String text) {
        return new McCommandResult(false, text);
    }

    public boolean isOk() { return ok; }
    public String getText() { return text; }
}
