package com.tamid.lahapsid;

import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.nio.file.Path;
import java.util.Properties;

public class Config {
    // Bot
    public static boolean botActive = false;
    public static int botMs = 100;          // delay in milliseconds, 100 - 1000
    public static boolean botCrits = true;  // wait for the fall, reset sprint -> critical hits
    public static boolean botCharge = true; // wait for full weapon charge
    public static int botSlot = 0;          // 0 = any slot, 1-9 = only this hotbar slot
    public static int botBind = -1;
    // Aa (aim tracker)
    public static boolean aimActive = false;
    public static int aimRadius = 30;       // 5 - 100
    public static int aimFov = 180;         // 80 - 360
    public static boolean stickAim = true;
    public static int aimSmooth = 5;        // 0 = off, 1 - 10
    public static int vSpeed = 5;           // 0 - 10
    public static int hSpeed = 5;           // 0 - 10
    public static int aimSlot = 0;          // 0 = any slot, 1-9
    public static int aimBind = -1;
    // Misc
    public static boolean offOnDeath = true;
    public static boolean offOnTp = true;
    public static boolean fwActive = false;
    public static int fwDelay = 1500;       // ms between rockets
    public static int fwBind = -1;
    // Visuals
    public static boolean hud = true;
    public static int guiBind = GLFW.GLFW_KEY_RIGHT_SHIFT;
    public static int guiX = -1, guiY = -1; // window position, -1 = centered

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("tamidlahapsid.properties");
    }

    public static void load() {
        File f = file().toFile();
        if (!f.exists()) return;
        try (FileReader r = new FileReader(f)) {
            Properties p = new Properties();
            p.load(r);
            botActive = b(p, "botActive", false);
            botMs = i(p, "botMs", 100, 100, 1000);
            botCrits = b(p, "botCrits", true);
            botCharge = b(p, "botCharge", true);
            botSlot = i(p, "botSlot", 0, 0, 9);
            botBind = i(p, "botBind", -1, -1, 1000);
            aimActive = b(p, "aimActive", false);
            aimRadius = i(p, "aimRadius", 30, 5, 100);
            aimFov = i(p, "aimFov", 180, 80, 360);
            stickAim = b(p, "stickAim", true);
            aimSmooth = i(p, "aimSmooth", 5, 0, 10);
            vSpeed = i(p, "vSpeed", 5, 0, 10);
            hSpeed = i(p, "hSpeed", 5, 0, 10);
            aimSlot = i(p, "aimSlot", 0, 0, 9);
            aimBind = i(p, "aimBind", -1, -1, 1000);
            offOnDeath = b(p, "offOnDeath", true);
            offOnTp = b(p, "offOnTp", true);
            fwActive = b(p, "fwActive", false);
            fwDelay = i(p, "fwDelay", 1500, 500, 5000);
            fwBind = i(p, "fwBind", -1, -1, 1000);
            hud = b(p, "hud", true);
            guiBind = i(p, "guiBind", GLFW.GLFW_KEY_RIGHT_SHIFT, 0, 1000);
            guiX = i(p, "guiX", -1, -1, 10000);
            guiY = i(p, "guiY", -1, -1, 10000);
        } catch (Exception ignored) {}
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("botActive", "" + botActive);
        p.setProperty("botMs", "" + botMs);
        p.setProperty("botCrits", "" + botCrits);
        p.setProperty("botCharge", "" + botCharge);
        p.setProperty("botSlot", "" + botSlot);
        p.setProperty("botBind", "" + botBind);
        p.setProperty("aimActive", "" + aimActive);
        p.setProperty("aimRadius", "" + aimRadius);
        p.setProperty("aimFov", "" + aimFov);
        p.setProperty("stickAim", "" + stickAim);
        p.setProperty("aimSmooth", "" + aimSmooth);
        p.setProperty("vSpeed", "" + vSpeed);
        p.setProperty("hSpeed", "" + hSpeed);
        p.setProperty("aimSlot", "" + aimSlot);
        p.setProperty("aimBind", "" + aimBind);
        p.setProperty("offOnDeath", "" + offOnDeath);
        p.setProperty("offOnTp", "" + offOnTp);
        p.setProperty("fwActive", "" + fwActive);
        p.setProperty("fwDelay", "" + fwDelay);
        p.setProperty("fwBind", "" + fwBind);
        p.setProperty("hud", "" + hud);
        p.setProperty("guiBind", "" + guiBind);
        p.setProperty("guiX", "" + guiX);
        p.setProperty("guiY", "" + guiY);
        try (FileWriter w = new FileWriter(file().toFile())) {
            p.store(w, "Tamid Lahapsid");
        } catch (Exception ignored) {}
    }

    private static boolean b(Properties p, String k, boolean def) {
        return Boolean.parseBoolean(p.getProperty(k, "" + def));
    }

    private static int i(Properties p, String k, int def, int min, int max) {
        try {
            int v = Integer.parseInt(p.getProperty(k, "" + def));
            return Math.max(min, Math.min(max, v));
        } catch (Exception e) { return def; }
    }
}
