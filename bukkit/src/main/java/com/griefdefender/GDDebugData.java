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
package com.griefdefender;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.griefdefender.api.Tristate;
import com.griefdefender.cache.MessageCache;
import com.griefdefender.configuration.MessageStorage;
import com.griefdefender.util.HttpClient;
import net.kyori.text.Component;
import net.kyori.text.TextComponent;
import net.kyori.text.adapter.bukkit.TextAdapter;
import net.kyori.text.event.ClickEvent;
import net.kyori.text.format.TextColor;
import net.kyori.text.serializer.plain.PlainComponentSerializer;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.zip.GZIPOutputStream;

public class GDDebugData {
    private static final String BYTEBIN_ENDPOINT = "https://bytebin.lucko.me/post";
    private static final String DEBUG_VIEWER_URL = "https://griefprevention.github.io/debug/?";
    private static final MediaType PLAIN_TYPE = MediaType.parse("text/plain; charset=utf-8");

    private static final int MAX_LINES = 5000;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
    private static final Component GD_TEXT = TextComponent.builder("").append("[", TextColor.WHITE).append("GD", TextColor.AQUA).append("] ", TextColor.WHITE).build();

    private final CommandSender source;
    private final List<String> header;
    private final List<String> records;
    private final long startTime = System.currentTimeMillis();
    private boolean verbose;
    private OfflinePlayer target;

    public GDDebugData(CommandSender source, OfflinePlayer target, boolean verbose) {
        this.source = source;
        this.target = target;
        this.verbose = verbose;
        this.records = new ArrayList<>();
        this.header = new ArrayList<>();
        this.header.add("# GriefDefender Debug Log");
        this.header.add("#### This file was automatically generated by [GriefDefender](https://github.com/MinecraftPortCentral/GriefDefender) ");
        this.header.add("");
        this.header.add("### Metadata");
        this.header.add("| Key | Value |");
        this.header.add("|-----|-------|");
        this.header.add("| GD Version | " + GriefDefenderPlugin.IMPLEMENTATION_VERSION + "|");
        this.header.add("| Bukkit Version | " + Bukkit.getVersion() + "|");
        final Plugin lpContainer = Bukkit.getPluginManager().getPlugin("luckperms");
        if (lpContainer != null) {
            final String version = lpContainer.getDescription().getVersion();
            if (version != null) {
                this.header.add("| LuckPerms Version | " + version);
            }
        }
        this.header.add("| " + MessageCache.getInstance().LABEL_USER + " | " + (this.target == null ? "ALL" : this.target.getName()) + "|");
        this.header.add("| " + MessageCache.getInstance().DEBUG_RECORD_START + " | " + DATE_FORMAT.format(new Date(this.startTime)) + "|");
    }

    public void addRecord(String flag, String trust, String source, String target, String location, String user, String permission, Tristate result) {
        if (this.records.size() < MAX_LINES) {
            this.records.add("| " + flag + " | " + trust + " | " + source + " | " + target + " | " + location + " | " + user + " | " + permission + " | " + result + " | ");
        } else {
            TextAdapter.sendComponent(this.source, TextComponent.builder("").append("MAX DEBUG LIMIT REACHED!").append("\n")
                    .append("Pasting output...", TextColor.GREEN).build());
            this.pasteRecords();
            this.records.clear();
            GriefDefenderPlugin.debugActive = false;
            TextAdapter.sendComponent(this.source, TextComponent.builder("").append(GD_TEXT).append("Debug ", TextColor.GRAY).append("OFF", TextColor.RED).build());
        }
    }

    public CommandSender getSource() {
        return this.source;
    }

    public OfflinePlayer getTarget() {
        return this.target;
    }

    public boolean isRecording() {
        return !this.verbose;
    }

    public void setTarget(OfflinePlayer user) {
        this.target = user;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void pasteRecords() {
        if (this.records.isEmpty()) {
            TextAdapter.sendComponent(this.source, MessageCache.getInstance().DEBUG_NO_RECORDS);
            return;
        }

        final long endTime = System.currentTimeMillis();
        List<String> debugOutput = new ArrayList<>(this.header);
        final String RECORD_END = PlainComponentSerializer.INSTANCE.serialize(MessageCache.getInstance().DEBUG_RECORD_END);
        final String TIME_ELAPSED = PlainComponentSerializer.INSTANCE.serialize(MessageCache.getInstance().DEBUG_TIME_ELAPSED);
        final String OUTPUT = PlainComponentSerializer.INSTANCE.serialize(MessageCache.getInstance().LABEL_OUTPUT);
        final String FLAG = PlainComponentSerializer.INSTANCE.serialize(MessageCache.getInstance().LABEL_FLAG);
        final String TRUST = PlainComponentSerializer.INSTANCE.serialize(MessageCache.getInstance().LABEL_TRUST);
        final String LOCATION = PlainComponentSerializer.INSTANCE.serialize(MessageCache.getInstance().LABEL_LOCATION);
        final String SOURCE = PlainComponentSerializer.INSTANCE.serialize(MessageCache.getInstance().LABEL_SOURCE);
        final String TARGET = PlainComponentSerializer.INSTANCE.serialize(MessageCache.getInstance().LABEL_TARGET);
        final String USER = PlainComponentSerializer.INSTANCE.serialize(MessageCache.getInstance().LABEL_USER);
        final String PERMISSION = PlainComponentSerializer.INSTANCE.serialize(MessageCache.getInstance().LABEL_PERMISSION);
        final String RESULT = PlainComponentSerializer.INSTANCE.serialize(MessageCache.getInstance().LABEL_RESULT);
        debugOutput.add("| " + RECORD_END + " | " + DATE_FORMAT.format(new Date(endTime)) + "|");
        long elapsed = (endTime - startTime) / 1000L; 
        debugOutput.add("| " + TIME_ELAPSED + " | " + elapsed + " seconds" + "|");
        debugOutput.add("");
        debugOutput.add("### " + OUTPUT) ;
        debugOutput.add("| " + FLAG + " | " + TRUST + " | " + SOURCE + " | " + TARGET + " | " + LOCATION + " | " + USER + " | " + PERMISSION + " | " + RESULT + " |");
        debugOutput.add("|------|-------|--------|--------|----------|------|------------|--------|");

        debugOutput.addAll(this.records);

        String content = String.join("\n", debugOutput);

        String pasteId;
        try {
            pasteId = postContent(content);
        } catch (Exception e) {
            TextAdapter.sendComponent(this.source, GriefDefenderPlugin.getInstance().messageData.getMessage(MessageStorage.DEBUG_ERROR_UPLOAD,
                    ImmutableMap.of("content", TextComponent.of(e.getMessage(), TextColor.WHITE))));
            return;
        }

        String url = DEBUG_VIEWER_URL + pasteId;

        URL jUrl;
        try {
            jUrl = new URL(url);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        TextAdapter.sendComponent(this.source, TextComponent.builder()
                .append(MessageCache.getInstance().DEBUG_PASTE_SUCCESS)
                .append(" : " + url, TextColor.GREEN)
                .clickEvent(ClickEvent.openUrl(jUrl.toString())).build());
    }

    private static String postContent(String content) throws IOException {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        try (GZIPOutputStream writer = new GZIPOutputStream(byteOut)) {
            writer.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        RequestBody body = RequestBody.create(PLAIN_TYPE, byteOut.toByteArray());

        Request.Builder requestBuilder = new Request.Builder()
                .url(BYTEBIN_ENDPOINT)
                .header("Content-Encoding", "gzip")
                .post(body);

        Request request = requestBuilder.build();
        try (Response response = HttpClient.makeCall(request)) {
            try (ResponseBody responseBody = response.body()) {
                if (responseBody == null) {
                    throw new RuntimeException("No response");
                }

                try (InputStream inputStream = responseBody.byteStream()) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                        JsonObject object = new Gson().fromJson(reader, JsonObject.class);
                        return object.get("key").getAsString();
                    }
                }
            }
        }
    }
}
