/*
 * This file is part of GriefDefender, licensed under the MIT License (MIT).
 *
 * Copyright (c) bloodmc
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.griefdefender.configuration.serializer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.google.common.reflect.TypeToken;
import com.griefdefender.api.permission.Context;
import com.griefdefender.api.permission.ContextKeys;
import com.griefdefender.api.permission.flag.Flag;
import com.griefdefender.permission.flag.CustomFlagData;
import com.griefdefender.permission.flag.GDCustomFlagDefinition;
import com.griefdefender.registry.FlagRegistryModule;

import net.kyori.text.Component;
import net.kyori.text.TextComponent;
import net.kyori.text.serializer.legacy.LegacyComponentSerializer;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import ninja.leaping.configurate.objectmapping.serialize.TypeSerializer;

public class CustomFlagSerializer implements TypeSerializer<GDCustomFlagDefinition> {

    @Override
    public GDCustomFlagDefinition deserialize(TypeToken<?> type, ConfigurationNode node) throws ObjectMappingException {
        final String flagDisplayName = node.getKey().toString();
        final boolean enabled = node.getNode("enabled").getBoolean();
        final String descr = node.getNode("description").getString();
        Component description = TextComponent.empty();
        if (descr != null) {
            description = LegacyComponentSerializer.legacy().deserialize(descr, '&');
        }

        List<String> contextList = node.getNode("contexts").getList(TypeToken.of(String.class));
        List<String> permissionList = node.getNode("permissions").getList(TypeToken.of(String.class));
        if (permissionList == null) {
            throw new ObjectMappingException("No permissions found for flag definition '" + flagDisplayName + "'. You must specify at least 1 or more permissions.");
        }

        List<CustomFlagData> flagDataList = new ArrayList<>();
        for (String permissionEntry : permissionList) {
            String permission = permissionEntry.replace(" ", "");
            String[] parts = permission.split(",");
            Flag linkedFlag = null;
            Set<Context> flagContexts = new HashSet<>();
            for (String part : parts) {
                String[] split =  part.split("=");
                String key = split[0];
                String value = split[1];
                // Handle linked Flag
                if (key.equalsIgnoreCase("flag")) {
                    final String flagName = value;
                    linkedFlag = FlagRegistryModule.getInstance().getById(flagName).orElse(null);
                    if (linkedFlag == null) {
                        throw new ObjectMappingException("Input '" + flagName + "' is not a valid GD flag to link to.");
                    }
                } else { //contexts
                    // validate context key
                    switch (key) {
                        case ContextKeys.SOURCE:
                        case ContextKeys.TARGET:
                            if (!value.contains(":") && !value.contains("#")) {
                                value = "minecraft:" + value;
                            }
                            flagContexts.add(new Context(key, value));
                            break;
                        case "used_item":
                        case "item_name":
                        case ContextKeys.CLAIM_DEFAULT:
                        case ContextKeys.CLAIM_OVERRIDE:
                        case ContextKeys.STATE:
                            flagContexts.add(new Context(key, value));
                            break;
                        default:
                            throw new ObjectMappingException("Invalid context '" + key + "' with value '" + value + "'.");
                    }
                }
            }
            if (linkedFlag == null) {
                throw new ObjectMappingException("No linked flag specified. You need to specify 'flag=<flagname>'.");
            }

            flagDataList.add(new CustomFlagData(linkedFlag, flagContexts));
        }
        final GDCustomFlagDefinition flagDefinition = new GDCustomFlagDefinition(flagDataList, flagDisplayName, description);
        flagDefinition.setIsEnabled(enabled);
        Set<Context> contexts = new HashSet<>();
        if (contextList != null) {
            for (String context : contextList) {
                final String parts[] = context.split("=");
                if (parts.length <= 1) {
                    throw new ObjectMappingException("Invalid context '" + context + "' for flag definition '" + flagDisplayName + "'. Skipping...");
                }
                final String key = parts[0];
                final String value = parts[1];
                if (key.equalsIgnoreCase("default") || key.equalsIgnoreCase("gd_claim_default")) {
                    if (!value.equalsIgnoreCase("global") && !value.equalsIgnoreCase("basic") && !value.equalsIgnoreCase("admin")
                            && !value.equalsIgnoreCase("subdivision") && !value.equalsIgnoreCase("town")) {
                        throw new ObjectMappingException("Invalid context '" + key + "' with value '" + value + "'.");
                    }
                    contexts.add(new Context("gd_claim_default", value));
                } else if (key.equalsIgnoreCase("override") || key.equalsIgnoreCase("gd_claim_override")) {
                    if (!value.equalsIgnoreCase("global") && !value.equalsIgnoreCase("basic") && !value.equalsIgnoreCase("admin")
                            && !value.equalsIgnoreCase("subdivision") && !value.equalsIgnoreCase("town")) {
                        // try UUID
                        if (value.length() == 36) {
                            UUID uuid = null;
                            try {
                                uuid = UUID.fromString(value);
                            } catch (IllegalArgumentException e) {
                                throw new ObjectMappingException("Invalid context '" + key + "' with value '" + value + "'.");
                            }
                        } else {
                            throw new ObjectMappingException("Invalid context '" + key + "' with value '" + value + "'.");
                        }
                    }
                    contexts.add(new Context("gd_claim_override", value));
                } else {
                    contexts.add(new Context(key, value));
                }
            }
            flagDefinition.setDefinitionContexts(contexts);
        }
        return flagDefinition;
    }

    @Override
    public void serialize(TypeToken<?> type, GDCustomFlagDefinition obj, ConfigurationNode node) throws ObjectMappingException {
        node.getNode("enabled").setValue(obj.isEnabled());
        String description = "";
        if (obj.getDescription() != TextComponent.empty()) {
            description = LegacyComponentSerializer.legacy().serialize((Component) obj.getDescription(), '&');
            node.getNode("description").setValue(description);
        }

        if (!obj.getDefinitionContexts().isEmpty()) {
            List<String> contextList = new ArrayList<>();
            ConfigurationNode contextNode = node.getNode("contexts");
            for (Context context : obj.getDefinitionContexts()) {
                contextList.add(context.getKey().toLowerCase() + "=" + context.getValue().toLowerCase());
            }
            contextNode.setValue(contextList);
        }
        ConfigurationNode permissionNode = node.getNode("permissions");
        List<String> permissions = new ArrayList<>();
        for (CustomFlagData flagData : obj.getFlagData()) {
            int count = 0;
            final Flag flag = flagData.getFlag();
            final Set<Context> dataContexts = flagData.getContexts();
            String permission = "";
            if (count > 0) {
                permission += ", ";
            }
            permission += "flag=" + flag.getName().toLowerCase();
            count++;

            for (Context context : dataContexts) {
                String key = context.getKey();
                permission += ", " + key + "=" + context.getValue();
            }

            permissions.add(permission);
        }
        permissionNode.setValue(permissions);
    }

}
