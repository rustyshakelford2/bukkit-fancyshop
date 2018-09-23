package net.miscjunk.fancyshop;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static net.miscjunk.fancyshop.PendingCommand.Type.CLONE_STAGE_TWO;

public class FancyShopCommandExecutor implements CommandExecutor {
    private FancyShop plugin;
    boolean flagsInstalled;
    Map<UUID, PendingCommand> pending;
    Map<UUID, BukkitTask> tasks;

    public FancyShopCommandExecutor(FancyShop plugin) {
        this.plugin = plugin;
        this.pending = new HashMap<>();
        this.tasks = new HashMap<>();
        flagsInstalled = Bukkit.getServer().getPluginManager().isPluginEnabled("Flags");
        if (flagsInstalled) {
            Bukkit.getLogger().info("Found Flags, enabling region support");
            io.github.alshain01.flags.Registrar flagsRegistrar = io.github.alshain01.flags.Flags.getRegistrar();
            io.github.alshain01.flags.Flag flag = flagsRegistrar.register("FancyShop", "Allow creating shops", false, "FancyShop", "Entering shops area", "Leaving shops area");
        } else {
            Bukkit.getLogger().info("Flags is not installed, disabling region support");
        }
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Must be a player.");
            return true;
        }

        Player p = (Player) sender;

        if (args.length < 1) {
            printUsage(sender);
        } else if (args[0].equals("create")) {
            create(p, cmd, label, args);
        } else if (args[0].equals("remove")) {
            remove(p, cmd, label, args);
        } else if (args[0].equals("setadmin")) {
            setAdmin(p, cmd, label, args);
        } else if (args[0].equals("currency")) {
            currency(p, cmd, label, args);
        } else if (args[0].equals("rename")) {
            rename(p, cmd, label, args);
        } else if (args[0].equals("clone")) { // simpleauthority start
            clone(p, cmd, label, args);
        } else { // simpleauthority end
            printUsage(sender);
        }
        return true;
    }

    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!pending.containsKey(event.getPlayer().getUniqueId())) return;
        PendingCommand cmd = pending.get(event.getPlayer().getUniqueId());
        switch (cmd.getType()) {
            case CREATE:
                if (event.getClickedBlock() != null && event.getClickedBlock().getState() instanceof InventoryHolder) {
                    event.setCancelled(true);
                    create(event.getPlayer(), ((InventoryHolder) event.getClickedBlock().getState()).getInventory());
                }
                break;
            case REMOVE:
                if (event.getClickedBlock() != null && event.getClickedBlock().getState() instanceof InventoryHolder) {
                    event.setCancelled(true);
                    remove(event.getPlayer(), ((InventoryHolder) event.getClickedBlock().getState()).getInventory());
                }
                break;
            case SETADMIN:
                if (event.getClickedBlock() != null && event.getClickedBlock().getState() instanceof InventoryHolder) {
                    event.setCancelled(true);
                    setAdmin(event.getPlayer(), ((InventoryHolder) event.getClickedBlock().getState()).getInventory(),
                            cmd.getArgs()[0].equals("true"));
                }
                break;
            case RENAME:
                if (event.getClickedBlock() != null && event.getClickedBlock().getState() instanceof InventoryHolder) {
                    event.setCancelled(true);
                    rename(event.getPlayer(), ((InventoryHolder) event.getClickedBlock().getState()).getInventory(), cmd.getArgs());
                }
                break;
            // simpleauthority start
            case CLONE_STAGE_ONE:
                if (event.getClickedBlock() != null && event.getClickedBlock().getState() instanceof InventoryHolder) {
                    event.setCancelled(true);
                    cloneInitial(event.getPlayer(), ((InventoryHolder) event.getClickedBlock().getState()).getInventory());
                }
                break;
            case CLONE_STAGE_TWO:
                if (event.getClickedBlock() != null && event.getClickedBlock().getState() instanceof InventoryHolder) {
                    if (!cmd.hasPendingInventory()) {
                        Chat.e(event.getPlayer(), I18n.s("clone.no-shop-selected"));
                        return;
                    }
                    event.setCancelled(true);
                    cloneFinal(event.getPlayer(), cmd.getPendingInventory(), ((InventoryHolder) event.getClickedBlock().getState()).getInventory());
                }
                break;
            // simpleauthority ned
        }
    }

    private void remove(Player player, Inventory inv) {
        if (Shop.isShop(inv)) {
            Shop shop = Shop.fromInventory(inv);
            if (shop == null) return;
            if (!shop.getOwner().equals(player.getUniqueId()) && !player.hasPermission("fancyshop.remove")) {
                Chat.e(player, I18n.s("remove.owner"));
            } else {
                ShopRepository.remove(shop);
                Shop.removeShop(shop.getLocation());
                Chat.s(player, I18n.s("remove.confirm"));
            }
        } else {
            Chat.e(player, I18n.s("remove.no-shop"));
        }
        clearPending(player);
    }

    private void create(Player player, Inventory inv) {
        if (Shop.isShop(inv)) {
            Chat.e(player, I18n.s("create.exists"));
        } else if (!regionAllows(player, inv)) {
            Chat.e(player, I18n.s("create.region"));
        } else {
            Shop shop = Shop.fromInventory(inv, player.getUniqueId());
            if (shop == null) return;
            ShopRepository.store(shop);
            Chat.s(player, I18n.s("create.confirm"));
            Chat.i(player, I18n.s("create.confirm2"));
            Chat.i(player, I18n.s("create.confirm3"));
            shop.edit(player);
        }
        clearPending(player);
    }

    private void rename(Player player, Inventory inv, String[] args) {
        if (Shop.isShop(inv)) {
            Shop shop = Shop.fromInventory(inv);
            if (shop == null) return;
            if (!shop.getOwner().equals(player.getUniqueId())) {
                Chat.e(player, I18n.s("rename.owner"));
            } else {
                StringBuilder name = new StringBuilder(args[1]);
                for (int i = 2; i < args.length; i++) {
                    name.append(" ").append(args[i]);
                }
                shop.setName(name.toString());
                ShopRepository.store(shop);
                Chat.s(player, I18n.s("rename.confirm", name.toString()));
            }
        } else {
            Chat.e(player, I18n.s("rename.no-shop"));
        }
        clearPending(player);
    }

    private void setAdmin(Player player, Inventory inv, boolean admin) {
        if (!Shop.isShop(inv)) {
            Chat.e(player, I18n.s("setadmin.no-shop"));
        } else {
            Shop shop = Shop.fromInventory(inv);
            if (shop == null) return;
            shop.setAdmin(admin);
            ShopRepository.store(shop);
            if (admin) {
                Chat.s(player, I18n.s("setadmin.confirm-true"));
            } else {
                Chat.s(player, I18n.s("setadmin.confirm-false"));
            }
        }
        clearPending(player);
    }

    private void remove(Player player, Command cmd, String label, String[] args) {
        if (!player.hasPermission("fancyshop.create")) { //not typo - can't remove if we can't create
            Chat.e(player, I18n.s("remove.permission"));
            return;
        } else if (args.length > 1) {
            Chat.e(player, I18n.s("remove.usage"));
            return;
        }
        Chat.i(player, I18n.s("remove.prompt"));
        setPending(player, new PendingCommand(PendingCommand.Type.REMOVE));
    }

    private void create(Player player, Command cmd, String label, String[] args) {
        if (!player.hasPermission("fancyshop.create")) {
            Chat.e(player, I18n.s("create.permission"));
            return;
        } else if (args.length > 1) {
            Chat.e(player, I18n.s("create.usage"));
            return;
        }
        Chat.i(player, I18n.s("create.prompt"));
        setPending(player, new PendingCommand(PendingCommand.Type.CREATE));
    }

    private void rename(Player player, Command cmd, String label, String[] args) {
        if (!player.hasPermission("fancyshop.rename")) {
            Chat.e(player, I18n.s("rename.permission"));
            return;
        } else if (args.length < 2) {
            Chat.e(player, I18n.s("rename.usage"));
            return;
        }
        Chat.i(player, I18n.s("rename.prompt"));
        setPending(player, new PendingCommand(PendingCommand.Type.RENAME, args));
    }

    private void setAdmin(Player player, Command cmd, String label, String[] args) {
        if (!player.hasPermission("fancyshop.setadmin")) {
            Chat.e(player, I18n.s("setadmin.permission"));
            return;
        }
        if (args.length == 1 || args.length == 2 && args[1].equals("true")) {
            Chat.i(player, I18n.s("setadmin.prompt-true"));
            setPending(player, new PendingCommand(PendingCommand.Type.SETADMIN, "true"));
        } else if (args.length == 2 && args[1].equals("false")) {
            Chat.i(player, I18n.s("setadmin.prompt-false"));
            setPending(player, new PendingCommand(PendingCommand.Type.SETADMIN, "false"));
        } else {
            Chat.e(player, I18n.s("setadmin.usage"));
        }
    }

    private void currency(Player player, Command cmd, String label, String[] args) {
        if (!player.hasPermission("fancyshop.currency")) {
            Chat.e(player, I18n.s("currency.permission"));
            return;
        }
        if (args.length < 2) {
            Chat.e(player, I18n.s("currency.usage"));
            return;
        }
        StringBuilder name = new StringBuilder(args[1]);
        for (int i = 2; i < args.length; i++) {
            name.append(" ").append(args[i]);
        }

        if (CurrencyManager.getInstance().isCustomCurrency(name.toString())) {
            Chat.e(player, I18n.s("currency.exists"));
            return;
        }
        if (player.getInventory().getItemInMainHand() == null || player.getInventory().getItemInMainHand().getAmount() == 0) {
            Chat.e(player, I18n.s("currency.empty"));
            return;
        }
        CurrencyManager.getInstance().addCustomCurrency(name.toString(), player.getItemInHand());
        Chat.s(player, I18n.s("currency.confirm", name.toString()));
    }

    // simpleauthority start
    private void clone(Player player, Command cmd, String label, String[] args) {
        if (!player.hasPermission("fancyshop.clone")) {
            Chat.e(player, I18n.s("clone.permission"));
            return;
        }
        Chat.i(player, I18n.s("clone.prompt-1"));
        setPending(player, new PendingCommand(PendingCommand.Type.CLONE_STAGE_ONE));
    }

    private void cloneInitial(Player player, Inventory holder) {
        if (!player.hasPermission("fancyshop.clone")) { // player has lost permission between when they used the command and now
            Chat.e(player, I18n.s("clone.permission"));
            clearPending(player);
            return;
        }

        if (!Shop.isShop(holder)) {
            Chat.e(player, I18n.s("clone.block-is-not-shop"));
            clearPending(player);
            clone(player, null, null, null);
            return;
        }

        Chat.i(player, I18n.s("clone.prompt-2"));
        PendingCommand pending = new PendingCommand(CLONE_STAGE_TWO);
        pending.setPendingInventory(holder);
        setPending(player, pending);
    }

    private void cloneFinal(Player player, Inventory from, Inventory to) {
        if (!player.hasPermission("fancyshop.clone")) { // player has lost permission between when they clicked the first chest and now
            Chat.e(player, I18n.s("clone.permission"));
            clearPending(player);
            return;
        }

        if (Shop.isShop(to)) {
            Chat.e(player, I18n.s("clone.block-is-already-shop"));
            clearPending(player);
            cloneInitial(player, from);
            return;
        }

        Shop shopFrom = Shop.fromInventory(from);
        if (shopFrom == null) return;

        Shop shopTo = shopFrom.clone(new ShopLocation(to.getLocation()));
        Chat.s(player, I18n.s("clone.shop-copied"));
        ShopRepository.store(shopTo);
        Shop.shopMap.put(shopTo.location, shopTo);
        shopTo.edit(player);
        clearPending(player);
    }
    // simpleauthority end

    private boolean regionAllows(Player player, Inventory inv) {
        if (!flagsInstalled) return true;
        if (player.hasPermission("fancyshop.create.anywhere")) return true;
        io.github.alshain01.flags.Registrar flagsRegistrar = io.github.alshain01.flags.Flags.getRegistrar();
        io.github.alshain01.flags.Flag flag = flagsRegistrar.getFlag("FancyShop");
        InventoryHolder h = inv.getHolder();
        Location l;
        io.github.alshain01.flags.area.Area area;
        if (h instanceof BlockState) {
            l = ((BlockState) h).getLocation();
            area = io.github.alshain01.flags.CuboidType.getActive().getAreaAt(l);
        } else if (h instanceof DoubleChest) {
            l = ((DoubleChest) h).getLocation();
            area = io.github.alshain01.flags.CuboidType.getActive().getAreaAt(l);
        } else {
            area = io.github.alshain01.flags.CuboidType.DEFAULT.getAreaAt(player.getLocation());
        }
        Set<String> trusted = area.getPlayerTrustList(flag);
        if (trusted.contains(player.getName().toLowerCase())) {
            return true;
        } else {
            for (String s : area.getPermissionTrustList(flag)) {
                if (player.hasPermission(s)) return true;
            }
            return (trusted.isEmpty() && area.getValue(flag, false));
        }
    }

    private void setPending(Player player, PendingCommand cmd) {
        final UUID id = player.getUniqueId();
        if (tasks.containsKey(id)) {
            BukkitTask task = tasks.get(id);
            if (task != null) task.cancel();
        }
        pending.put(id, cmd);
        tasks.put(id, new BukkitRunnable() {
            public void run() {
                pending.remove(id);
            }
        }.runTaskLater(plugin, 60 * 20));
    }

    private void clearPending(Player player) {
        final UUID id = player.getUniqueId();
        if (tasks.containsKey(id)) {
            BukkitTask task = tasks.get(id);
            if (task != null) task.cancel();
            tasks.remove(id);
        }
        pending.remove(id);
    }

    public boolean hasPending(Player player) {
        return pending.containsKey(player.getUniqueId());
    }

    public void printUsage(CommandSender sender) {
        Chat.i(sender, I18n.s("usage.main"));
        if (sender instanceof Player && ((Player) sender).hasPermission("fancyshop.setadmin")) {
            Chat.i(sender, I18n.s("usage.setadmin"));
        }
        if (sender instanceof Player && sender.hasPermission("fancyshop.clone")) {
            Chat.i(sender, I18n.s("usage.clone"));
        }
        if (sender instanceof Player && ((Player) sender).hasPermission("fancyshop.currency")) {
            Chat.i(sender, I18n.s("usage.currency"));
        }
    }
}
