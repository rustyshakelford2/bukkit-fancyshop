package net.miscjunk.fancyshop;

import org.bukkit.inventory.Inventory;

public class PendingCommand {
    enum Type {CREATE, REMOVE, SETADMIN, RENAME, CLONE_STAGE_ONE, CLONE_STAGE_TWO}

    private Type type;
    private String[] args;
    private Inventory pendingInventory; // simpleauthority

    public PendingCommand(Type type) {
        this(type, new String[]{});
    }

    public PendingCommand(Type type, String... args) {
        this.type = type;
        this.args = args;
    }

    public Type getType() {
        return type;
    }

    public String[] getArgs() {
        return args;
    }

    // simpleauthority start
    public void setPendingInventory(Inventory pendingInventory) {
        this.pendingInventory = pendingInventory;
    }

    public boolean hasPendingInventory() {
        return getPendingInventory() != null;
    }

    public Inventory getPendingInventory() {
        return pendingInventory;
    }
    // simpleauthority end
}
