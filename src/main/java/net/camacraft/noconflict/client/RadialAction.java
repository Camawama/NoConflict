package net.camacraft.noconflict.client;

/**
 * Something selectable on the radial menu.
 * KeyMapping gains this interface at runtime via KeyMappingMixin; F3 actions implement it directly,
 * so the F3 menu no longer has to register ghost KeyMappings in vanilla's static maps.
 */
public interface RadialAction {
    /** Translation key (or literal fallback) used as the item's label. */
    String getActionName();
}
