package com.nannerss.craftcontrollegacy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang.WordUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.nannerss.bananalib.BananaLib;
import com.nannerss.bananalib.config.ConfigManager;
import com.nannerss.bananalib.messages.Console;
import com.nannerss.bananalib.utils.Registrar;
import com.nannerss.bananalib.utils.Utils;
import com.nannerss.craftcontrollegacy.commands.BlacklistCommand;
import com.nannerss.craftcontrollegacy.commands.CraftControlCommand;
import com.nannerss.craftcontrollegacy.commands.ItemFactoryCommand;
import com.nannerss.craftcontrollegacy.commands.RecipeCommand;
import com.nannerss.craftcontrollegacy.data.BlacklistedItems;
import com.nannerss.craftcontrollegacy.data.CustomRecipe;
import com.nannerss.craftcontrollegacy.data.CustomRecipe.CustomRecipeType;
import com.nannerss.craftcontrollegacy.data.PlayerCache;
import com.nannerss.craftcontrollegacy.gui.AdminGUI;
import com.nannerss.craftcontrollegacy.listeners.ClickListener;
import com.nannerss.craftcontrollegacy.listeners.CloseListener;
import com.nannerss.craftcontrollegacy.listeners.CraftListener;
import com.nannerss.craftcontrollegacy.listeners.PlayerListener;
import com.nannerss.craftcontrollegacy.tasks.GUIUpdateTask;
import com.nannerss.craftcontrollegacy.utils.Metrics;
import com.nannerss.craftcontrollegacy.utils.Pagination;
import com.nannerss.craftcontrollegacy.utils.Updater;
import com.nannerss.craftcontrollegacy.utils.comparators.RecipeTypeComparator;

import lombok.Getter;
import net.md_5.bungee.api.chat.TextComponent;

public class CraftControl extends JavaPlugin {
    
    @Getter
    private static CraftControl instance;
    
    @Getter
    private static ConfigManager blacklistedItems;
    
    @Getter
    private static ConfigManager customRecipes;
    
    @Getter
    private static final Cache<UUID, PlayerCache> inventoryCache = CacheBuilder.newBuilder().maximumSize(1000).build();
    
    @Getter
    private static final Cache<UUID, String> editSessions = CacheBuilder.newBuilder().maximumSize(1000).build();
    
    @Getter
    private static final List<ItemStack> blacklistItemCache = new ArrayList<>();
    
    @Getter
    private static final List<ItemStack> recipeItemCache = new ArrayList<>();
    
    @Getter
    private static final List<Recipe> vanillaRecipes = new ArrayList<>();
    
    @Getter
    private static final Cache<String, CustomRecipe> recipeCache = CacheBuilder.newBuilder().maximumSize(1000).build();
    
    @Getter
    private static Pagination blacklistPagination;
    
    @Getter
    private static Pagination recipePagination;
    
    public static boolean old;
    
    @Override
    public void onEnable() {
        instance = this;
        BananaLib.setInstance(this);
        
        setupVanillaRecipes();
        
        old = Bukkit.getVersion().contains("1.8");
    
        blacklistedItems = new ConfigManager("blacklisted-items.yml", true);
        new BlacklistedItems();
        
        customRecipes = new ConfigManager("recipes.yml", true);
        ConfigurationSection section = customRecipes.getConfigurationSection("custom-recipes");
        if (section == null) {
            section = customRecipes.createSection("custom-recipes");
            customRecipes.saveConfig();
        }
    
        for (String key : section.getKeys(false)) {
            getRecipe(key);
        }
        addRecipesToList();
        
        AdminGUI.setupMainGui();
    
        Registrar.registerCommands(
                new CraftControlCommand(),
                new BlacklistCommand(),
                new RecipeCommand(),
                new ItemFactoryCommand());
        getServer().getPluginManager().registerEvents(new CraftListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(), this);
        getServer().getPluginManager().registerEvents(new ClickListener(), this);
        getServer().getPluginManager().registerEvents(new CloseListener(), this);
        
        blacklistPagination = new Pagination(28, blacklistItemCache);
        recipePagination = new Pagination(28, recipeItemCache);
        
        for (final Player p : Bukkit.getOnlinePlayers()) {
            getCache(p.getUniqueId());
        }
        
        new GUIUpdateTask().runTaskTimer(this, 100, 20);
        
        new Updater(69036) {
    
            @Override
            public void onUpdateAvailable() {
                Console.log(TextComponent.toPlainText(getUpdateMessage()));
            }
            
        }.runTaskAsynchronously(this);
        
        final Metrics metrics = new Metrics(this);
    }
    
    @Override
    public void onDisable() {
        instance = null;
        
        BlacklistedItems.setItems(blacklistItemCache);
        BlacklistedItems.save();
        
        inventoryCache.invalidateAll();
        blacklistItemCache.clear();
        
        getServer().getScheduler().cancelTasks(this);
    }
    
    public void addRecipesToList() {
        for (CustomRecipe customRecipe : recipeCache.asMap().values()) {
            if (customRecipe.getType() == CustomRecipeType.SHAPELESS) {
                final ItemStack item = new ItemStack(Material.DIAMOND_BLOCK);
                final ItemMeta meta = item.getItemMeta();
            
                meta.setDisplayName(Utils.colorize("&e&l") + customRecipe.getName());
                meta.setLore(Arrays.asList(
                        Utils.colorize("&3Name&f: ") + customRecipe.getName(),
                        Utils.colorize("&3Type&f: " + WordUtils.capitalize(customRecipe.getType().toString().toLowerCase())),
                        Utils.colorize("&3Result&f: " + (customRecipe.getResult().hasItemMeta() ? customRecipe.getResult().getItemMeta().getDisplayName() : WordUtils.capitalize(customRecipe.getResult().getType().toString().toLowerCase().replace("_", " "))))
                ));
                meta.addItemFlags(ItemFlag.values());
            
                item.setItemMeta(meta);
            
                recipeItemCache.add(item);
            } else if (customRecipe.getType() == CustomRecipeType.SHAPED) {
                final ItemStack item = new ItemStack(Material.EMERALD_BLOCK);
                final ItemMeta meta = item.getItemMeta();
            
                meta.setDisplayName(Utils.colorize("&e&l") + customRecipe.getName());
                meta.setLore(Arrays.asList(
                        Utils.colorize("&3Name&f: ") + customRecipe.getName(),
                        Utils.colorize("&3Type&f: " + WordUtils.capitalize(customRecipe.getType().toString().toLowerCase())),
                        Utils.colorize("&3Result&f: " + (customRecipe.getResult().hasItemMeta() ? customRecipe.getResult().getItemMeta().getDisplayName() : WordUtils.capitalize(customRecipe.getResult().getType().toString().toLowerCase().replace("_", " "))))
                ));
                meta.addItemFlags(ItemFlag.values());
            
                item.setItemMeta(meta);
            
                recipeItemCache.add(item);
            } else if (customRecipe.getType() == CustomRecipeType.FURNACE) {
                final ItemStack item = new ItemStack(Material.FURNACE);
                final ItemMeta meta = item.getItemMeta();
            
                meta.setDisplayName(Utils.colorize("&e&l") + customRecipe.getName());
                meta.setLore(Arrays.asList(
                        Utils.colorize("&3Name&f: ") + customRecipe.getName(),
                        Utils.colorize("&3Type&f: " + WordUtils.capitalize(customRecipe.getType().toString().toLowerCase())),
                        Utils.colorize("&3Result&f: " + (customRecipe.getResult().hasItemMeta() ? customRecipe.getResult().getItemMeta().getDisplayName() : WordUtils.capitalize(customRecipe.getResult().getType().toString().toLowerCase().replace("_", " "))))
                ));
                meta.addItemFlags(ItemFlag.values());
            
                item.setItemMeta(meta);
            
                recipeItemCache.add(item);
            }
        }
        
        recipeItemCache.sort(new RecipeTypeComparator());
    }
    
    public void setupVanillaRecipes() {
        final Iterator<Recipe> iterator = Bukkit.getServer().recipeIterator();
        
        while (iterator.hasNext()) {
            final Recipe recipe = iterator.next();
            
            vanillaRecipes.add(recipe);
        }
    }
    
    public static PlayerCache getCache(final UUID id) {
        PlayerCache cache = inventoryCache.getIfPresent(id);
        
        if (cache == null) {
            final Inventory inv = Bukkit.createInventory(null, 27, "Craft Control Admin");
            cache = new PlayerCache(inv, 0);
            
            inventoryCache.put(id, cache);
        }
        
        return cache;
    }
    
    public static CustomRecipe getRecipe(final String name) {
        CustomRecipe recipe = recipeCache.getIfPresent(name);
        
        if (recipe == null) {
            recipe = new CustomRecipe(name);
            
            recipeCache.put(name, recipe);
        }
        
        return recipe;
    }
    
}
