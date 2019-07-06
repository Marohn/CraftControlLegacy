package com.nannerss.craftcontrollegacy.utils;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import com.nannerss.craftcontrollegacy.CraftControl;

public class GUISounds {
    
    public static void playOpenSound(final Player p) {
        p.playSound(p.getLocation(), CraftControl.old ? Sound.valueOf("ENDERDRAGON_WINGS") : Sound.valueOf("ENTITY_ENDERDRAGON_FLAP"), 0.5F, 1F);
    }
    
    public static void playClickSound(final Player p) {
        p.playSound(p.getLocation(), CraftControl.old ? Sound.valueOf("CLICK") : Sound.valueOf("UI_BUTTON_CLICK"), 0.5F, 1F);
    }
    
    public static void playBassSound(final Player p) {
        p.playSound(p.getLocation(), CraftControl.old ? Sound.valueOf("NOTE_BASS") : Sound.valueOf("BLOCK_NOTE_BASS"), 0.5F, 1F);
    }
    
}
