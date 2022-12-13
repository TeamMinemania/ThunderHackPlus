package com.mrzak34.thunderhack.modules;

import com.mrzak34.thunderhack.gui.hud.HudEditorGui;
import com.mrzak34.thunderhack.gui.classic.ClassicGui;
import com.mrzak34.thunderhack.setting.Setting;
import java.util.ArrayList;
import java.util.List;

import com.mrzak34.thunderhack.util.Util;

public class Feature implements Util{

    public List<Setting> settings = new ArrayList<>();
    private String name;

    public Feature() {
    }

    public Feature(String name) {
        this.name = name;
    }

    public static boolean nullCheck() {
        return mc.player == null;
    }

    public static boolean fullNullCheck() {
        return mc.player == null || mc.world == null;
    }

    public String getName() {
        return this.name;
    }

    public List<Setting> getSettings() {
        return this.settings;
    }

    public boolean isEnabled() {
        if (this instanceof Module) {
            return ((Module) this).isOn();
        }
        return false;
    }

    public boolean isDisabled() {
        return !this.isEnabled();
    }

    public Setting register(Setting setting) {
        setting.setFeature(this);
        this.settings.add(setting);
        if (this instanceof Module && mc.currentScreen instanceof ClassicGui) {
            ClassicGui.getInstance().updateModule((Module) this);
        }
        if (this instanceof Module && mc.currentScreen instanceof HudEditorGui) {
            HudEditorGui.getInstance().updateModule((Module) this);
        }
        return setting;
    }

    public Setting getSettingByName(String name) {
        for (Setting setting : this.settings) {
            if (!setting.getName().equalsIgnoreCase(name)) continue;
            return setting;
        }
        return null;
    }

    public void reset() {
        for (Setting setting : this.settings) {
            setting.setValue(setting.getDefaultValue());
        }
    }

    public void clearSettings() {
        this.settings = new ArrayList<>();
    }
}

