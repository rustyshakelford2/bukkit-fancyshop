package net.miscjunk.fancyshop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.*;
import java.util.UUID;

public class ShopRepository {
    private static Plugin plugin;
    private static Connection db;

    public static void init(Plugin plugin) {
        if (ShopRepository.plugin != null || ShopRepository.db != null) {
            throw new RuntimeException("Already initialized");
        }
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdir();
        String dbPath = plugin.getDataFolder().getAbsolutePath() + File.separator + "shops.db";
        try {
            Class.forName("org.sqlite.JDBC");
            db = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Couldn't load sqlite library", e);
        } catch (SQLException e) {
            throw new RuntimeException("Couldn't open database", e);
        }
        ShopRepository.plugin = plugin;
        try {
            updateSchema();
        } catch (SQLException e) {
            throw new RuntimeException("Couldn't initialize database", e);
        }
    }

    public static void cleanup() {
        if (db != null) {
            try {
                db.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
            db = null;
        }
    }

    public static void updateSchema() throws SQLException {
        Statement stmt = db.createStatement();
        ResultSet rs = stmt.executeQuery("PRAGMA user_version");
        if (rs.next()) {
            int version = rs.getInt(1);
            if (version > 3) {
                throw new RuntimeException("Database is newer than plugin version");
            }
            switch (version) {
                /* Fall-through is intentional, we want ALL updates! */
                case 0:
                    stmt.execute("CREATE TABLE shops (" +
                            "location TEXT NOT NULL," +
                            "owner TEXT NOT NULL," +
                            "PRIMARY KEY (location)" +
                            ")");
                    stmt.execute("CREATE TABLE deals (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "shop_id INT NOT NULL," +
                            "item TEXT NOT NULL," +
                            "buy_price TEXT," +
                            "sell_price TEXT," +
                            "FOREIGN KEY (shop_id) REFERENCES shops(location)" +
                            ")");
                    stmt.execute("PRAGMA user_version=1");
                case 1:
                    stmt.execute("ALTER TABLE shops ADD COLUMN is_admin INT NOT NULL DEFAULT 0");
                    stmt.execute("PRAGMA user_version=2");
                case 2:
                    stmt.execute("ALTER TABLE shops ADD COLUMN name TEXT NOT NULL DEFAULT ''");
                    rs = stmt.executeQuery("SELECT * FROM shops");
                    while (rs.next()) {
                        String ownerName = rs.getString("owner");
                        UUID ownerId = plugin.getServer().getOfflinePlayer(ownerName).getUniqueId();
                        String name = I18n.s("shop.default-name", ownerName);
                        PreparedStatement update = db.prepareStatement("UPDATE shops SET owner=?, name=? WHERE location=?");
                        update.setString(1, ownerId.toString());
                        update.setString(2, name);
                        update.setString(3, rs.getString("location"));
                        update.execute();
                    }
                    stmt.execute("PRAGMA user_version=3");
            }
        } else {
            throw new RuntimeException("Couldn't get database schema version");
        }
    }

    public static boolean store(Shop shop) {
        try {
            PreparedStatement stmt = db.prepareStatement("INSERT OR REPLACE INTO shops VALUES (?, ?, ?, ?)");
            stmt.setString(1, shop.getLocation().toString());
            stmt.setString(2, shop.getOwner().toString());
            stmt.setBoolean(3, shop.isAdmin());
            stmt.setString(4, shop.getName());
            stmt.execute();
            stmt = db.prepareStatement("DELETE FROM deals WHERE shop_id=?");
            stmt.setString(1, shop.getLocation().toString());
            stmt.execute();
            for (Deal d : shop.deals) {
                storeDeal(shop, d);
            }
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void storeDeal(Shop shop, Deal deal) throws SQLException {
        PreparedStatement stmt = db.prepareStatement("INSERT INTO deals (shop_id, item, buy_price, sell_price) VALUES (?,?,?,?)");
        stmt.setString(1, shop.getLocation().toString());
        stmt.setString(2, CurrencyManager.itemToString(deal.getItem()));
        stmt.setString(3, CurrencyManager.itemToString(deal.getBuyPrice()));
        stmt.setString(4, CurrencyManager.itemToString(deal.getSellPrice()));
        stmt.execute();
    }

    public static boolean remove(Shop shop) {
        try {
            PreparedStatement stmt = db.prepareStatement("DELETE FROM deals WHERE shop_id=?");
            stmt.setString(1, shop.getLocation().toString());
            stmt.execute();
            stmt = db.prepareStatement("DELETE FROM shops WHERE location=?");
            stmt.setString(1, shop.getLocation().toString());
            stmt.execute();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static Shop load(ShopLocation location, Inventory inv) {
        try {
            PreparedStatement stmt = db.prepareStatement("SELECT * FROM shops WHERE location=?");
            stmt.setString(1, location.toString());
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) return null;
            UUID owner = UUID.fromString(rs.getString("owner"));
            boolean admin = rs.getBoolean("is_admin");
            String name = rs.getString("name");
            Shop shop = new Shop(location, inv, owner, name, admin);
            stmt = db.prepareStatement("SELECT * FROM deals WHERE shop_id=?");
            stmt.setString(1, location.toString());
            rs = stmt.executeQuery();
            while (rs.next()) {
                ItemStack item = CurrencyManager.stringToItem(rs.getString("item"));
                ItemStack buyPrice = CurrencyManager.stringToItem(rs.getString("buy_price"));
                ItemStack sellPrice = CurrencyManager.stringToItem(rs.getString("sell_price"));
                if (!CurrencyManager.getInstance().isCurrency(buyPrice)) buyPrice = null;
                if (!CurrencyManager.getInstance().isCurrency(sellPrice)) sellPrice = null;
                Deal d = new Deal(item, buyPrice, sellPrice);
                shop.deals.add(d);
            }
            shop.refreshView();
            return shop;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }
}
