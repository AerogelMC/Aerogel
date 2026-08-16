package net.minecraft.server.permissions;

import java.util.function.Predicate;

public interface PermissionProviderCheck<T> extends Predicate<T> { }
