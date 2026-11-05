package com.tamid.lahapsid;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

public class TamidClient implements ClientModInitializer {
    private final Map<Integer, Boolean> down = new HashMap<>();
    private long lastClick = 0;
    private long lastFw = 0;
    private long lastFrame = System.nanoTime();
    private int targetId = -1;
    private Vec3d lastPos = null;
    private Object lastWorld = null;
    private Object lastPlayer = null;

    @Override
    public void onInitializeClient() {
        Config.load();
        // START of the tick = the crosshair target is exactly the one the game will attack,
        // so the bot never swings at air because the target moved.
        ClientTickEvents.START_CLIENT_TICK.register(this::tick);
        HudRenderCallback.EVENT.register(this::frame);
    }

    /** True only on the tick the key goes down. */
    private boolean edge(MinecraftClient mc, int key) {
        if (key < 0) return false;
        boolean d = InputUtil.isKeyPressed(mc.getWindow().getHandle(), key);
        boolean prev = down.getOrDefault(key, false);
        down.put(key, d);
        return d && !prev;
    }

    private boolean slotOk(ClientPlayerEntity me, int slot) {
        return slot == 0 || me.getInventory().selectedSlot == slot - 1;
    }

    private boolean airborne(ClientPlayerEntity me) {
        return !me.isOnGround() && !me.isTouchingWater() && !me.isClimbing()
                && !me.hasVehicle() && !me.isFallFlying();
    }

    private void tick(MinecraftClient mc) {
        boolean gui = edge(mc, Config.guiBind);
        boolean bot = edge(mc, Config.botBind);
        boolean aim = edge(mc, Config.aimBind);
        boolean fw = edge(mc, Config.fwBind);

        ClientPlayerEntity me = mc.player;
        if (me == null || mc.world == null) {
            lastPos = null; lastWorld = null; lastPlayer = null;
            return;
        }

        boolean newPlayer = lastPlayer != null && lastPlayer != me;   // respawn creates a new player entity
        boolean newWorld = lastWorld != null && lastWorld != mc.world; // dimension change
        lastPlayer = me;

        // Off on death: health 0, death screen, or a fresh player entity (instant respawn)
        if (Config.offOnDeath && (me.isDead() || me.getHealth() <= 0f
                || mc.currentScreen instanceof DeathScreen || (newPlayer && !newWorld))) {
            turnOff(mc, "death");
        }

        // Off on teleport: world change or a big jump in one tick
        Vec3d now = me.getPos();
        if (Config.offOnTp) {
            double limit = me.isFallFlying() ? 14.0 : 6.0;
            if (newWorld || (lastPos != null && !newPlayer && lastPos.distanceTo(now) > limit)) {
                turnOff(mc, "teleport");
            }
        }
        lastPos = now;
        lastWorld = mc.world;

        if (mc.currentScreen != null) return;

        if (gui) { mc.setScreen(new ClickGui()); return; }
        if (bot) Config.botActive = !Config.botActive;
        if (aim) { Config.aimActive = !Config.aimActive; targetId = -1; }
        if (fw) Config.fwActive = !Config.fwActive;

        long t = System.currentTimeMillis();

        // Bot
        if (Config.botActive && t - lastClick >= Config.botMs && botReady(mc, me)) {
            lastClick = t;
            // Critical hits need you NOT sprinting: reset sprint right before the hit
            if (Config.botCrits && airborne(me) && me.isSprinting()) {
                me.setSprinting(false);
                if (mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(me,
                            ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                }
            }
            KeyBinding.onKeyPressed(InputUtil.fromTranslationKey(
                    mc.options.attackKey.getBoundKeyTranslationKey()));
        }

        // Auto firework (while gliding), never touches your selected hotbar slot
        if (Config.fwActive && me.isFallFlying() && t - lastFw >= Config.fwDelay && useFirework(mc, me)) {
            lastFw = t;
        }
    }

    private boolean botReady(MinecraftClient mc, ClientPlayerEntity me) {
        if (!me.isAlive() || !slotOk(me, Config.botSlot)) return false;
        if (!lookingAtRealEntity(mc)) return false;
        if (Config.botCharge && me.getAttackCooldownProgress(0.5f) < 0.95f) return false;
        // Rising? Wait until you start falling, that is when the hit is a critical.
        if (Config.botCrits && airborne(me) && me.getVelocity().y >= 0) return false;
        return true;
    }

    /** Swaps the rocket into the offhand with an inventory click, uses it, swaps back. Hotbar selection never changes. */
    private boolean useFirework(MinecraftClient mc, ClientPlayerEntity me) {
        if (mc.interactionManager == null) return false;
        if (me.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) {
            mc.interactionManager.interactItem(me, Hand.OFF_HAND);
            return true;
        }
        PlayerInventory inv = me.getInventory();
        int found = -1;
        for (int i = 0; i < 36; i++) {
            if (inv.getStack(i).isOf(Items.FIREWORK_ROCKET)) { found = i; break; }
        }
        if (found < 0) return false;
        int slotId = found < 9 ? 36 + found : found;
        int sync = me.playerScreenHandler.syncId;
        mc.interactionManager.clickSlot(sync, slotId, 40, SlotActionType.SWAP, me);
        mc.interactionManager.interactItem(me, Hand.OFF_HAND);
        mc.interactionManager.clickSlot(sync, slotId, 40, SlotActionType.SWAP, me);
        return true;
    }

    private void turnOff(MinecraftClient mc, String reason) {
        if (Config.botActive || Config.aimActive) {
            Config.botActive = false;
            Config.aimActive = false;
            targetId = -1;
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal("Tamid Lahapsid: Bot and Aa turned off (" + reason + ")"), true);
            }
        }
    }

    /** Crosshair must be on a living, visible, non-spectator entity (not air, not blocks, not vanished). */
    private boolean lookingAtRealEntity(MinecraftClient mc) {
        if (!(mc.crosshairTarget instanceof EntityHitResult hit)) return false;
        Entity e = hit.getEntity();
        if (e == null || !e.isAlive() || e.isInvisible() || e.isSpectator()) return false;
        if (e instanceof PlayerEntity p) return listed(mc, p);
        return true;
    }

    /** Vanished players are normally removed from the tab list. */
    private boolean listed(MinecraftClient mc, PlayerEntity p) {
        return mc.getNetworkHandler() != null
                && mc.getNetworkHandler().getPlayerListEntry(p.getUuid()) != null;
    }

    private void frame(DrawContext ctx, RenderTickCounter tc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        long nowN = System.nanoTime();
        float dt = Math.min((nowN - lastFrame) / 1_000_000_000f, 0.1f);
        lastFrame = nowN;
        if (mc.player == null || mc.world == null) return;

        if (Config.aimActive && mc.currentScreen == null) aim(mc, dt);

        if (Config.hud && !mc.options.hudHidden) {
            int y = 4;
            ctx.drawTextWithShadow(mc.textRenderer, "Tamid Lahapsid", 4, y, 0xFF3B82F6);
            y += 11;
            if (Config.botActive) { ctx.drawTextWithShadow(mc.textRenderer, "Bot [" + Config.botMs + "ms]", 4, y, 0xFFFFFFFF); y += 10; }
            if (Config.aimActive) { ctx.drawTextWithShadow(mc.textRenderer, "Aa [" + Config.aimRadius + "]", 4, y, 0xFFFFFFFF); y += 10; }
            if (Config.fwActive) { ctx.drawTextWithShadow(mc.textRenderer, "Firework", 4, y, 0xFFFFFFFF); }
        }
    }

    private boolean valid(ClientPlayerEntity me, PlayerEntity p, double r) {
        return p != me && p.isAlive() && !p.isSpectator() && !p.isInvisible()
                && listed(MinecraftClient.getInstance(), p) && me.distanceTo(p) <= r;
    }

    private boolean inFov(ClientPlayerEntity me, PlayerEntity p) {
        if (Config.aimFov >= 360) return true;
        Vec3d look = me.getRotationVec(1.0f).normalize();
        Vec3d to = p.getBoundingBox().getCenter().subtract(me.getEyePos()).normalize();
        double ang = Math.toDegrees(Math.acos(MathHelper.clamp(look.dotProduct(to), -1.0, 1.0)));
        return ang <= Config.aimFov / 2.0;
    }

    private PlayerEntity pickTarget(MinecraftClient mc) {
        ClientPlayerEntity me = mc.player;
        double r = Config.aimRadius;

        // Stick aim: keep the first target until it dies / leaves range (FOV only matters for NEW targets)
        if (Config.stickAim && targetId != -1) {
            Entity e = mc.world.getEntityById(targetId);
            if (e instanceof PlayerEntity p && valid(me, p, r)) return p;
            targetId = -1;
        }
        PlayerEntity best = null;
        double bd = Double.MAX_VALUE;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (!valid(me, p, r) || !inFov(me, p)) continue;
            double d = me.squaredDistanceTo(p);
            if (d < bd) { bd = d; best = p; }
        }
        targetId = best == null ? -1 : best.getId();
        return best;
    }

    private void aim(MinecraftClient mc, float dt) {
        ClientPlayerEntity me = mc.player;
        if (!me.isAlive() || !slotOk(me, Config.aimSlot)) return;
        PlayerEntity t = pickTarget(mc);
        if (t == null) return;

        Vec3d eye = me.getEyePos();
        Vec3d pos = t.getBoundingBox().getCenter();
        double dx = pos.x - eye.x, dy = pos.y - eye.y, dz = pos.z - eye.z;
        float tYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
        float tPitch = (float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180.0 / Math.PI);

        float dYaw = MathHelper.wrapDegrees(tYaw - me.getYaw());
        float dPitch = tPitch - me.getPitch();

        // Smooth aim: ease towards the target (big gap = fast, small gap = slow) instead of a flat speed
        if (Config.aimSmooth > 0) {
            float k = 24f / Config.aimSmooth;
            float f = 1f - (float) Math.exp(-k * dt);
            dYaw *= f;
            dPitch *= f;
        }

        float maxH = Config.hSpeed * 90f * dt;   // degrees per second = speed * 90
        float maxV = Config.vSpeed * 90f * dt;

        float yaw = me.getYaw() + MathHelper.clamp(dYaw, -maxH, maxH);
        float pitch = MathHelper.clamp(me.getPitch() + MathHelper.clamp(dPitch, -maxV, maxV), -90f, 90f);
        me.setYaw(yaw);
        me.setPitch(pitch);
        me.prevYaw = yaw;
        me.prevPitch = pitch;
    }
}
