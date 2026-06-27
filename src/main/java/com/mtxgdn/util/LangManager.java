package com.mtxgdn.util;

import com.google.gson.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LangManager {

    private static final Map<String, Map<String, String>> cache = new ConcurrentHashMap<>();
    private static final Gson gson = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private static String currentLang = "zh_cn";

    private LangManager() {
    }

    public static void setLanguage(String lang) {
        currentLang = lang;
    }

    public static String getLanguage() {
        return currentLang;
    }

    public static String get(String key) {
        return get(key, key);
    }

    public static String get(String key, String defaultValue) {
        Map<String, String> langMap = load(currentLang);
        return langMap.getOrDefault(key, defaultValue);
    }

    private static Map<String, String> load(String lang) {
        return cache.computeIfAbsent(lang, LangManager::loadInternal);
    }

    private static Map<String, String> loadInternal(String lang) {
        String path = "/data/mtxgdn/lang/" + lang + ".json";
        try (InputStream is = LangManager.class.getResourceAsStream(path)) {
            if (is == null) {
                return Collections.emptyMap();
            }
            try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                Map<String, String> map = gson.fromJson(reader, MAP_TYPE);
                return map != null ? map : Collections.emptyMap();
            }
        } catch (JsonSyntaxException e) {
            GameLogger.getLogger(LangManager.class).error("语言文件语法错误: " + path, e);
            return Collections.emptyMap();
        } catch (Exception e) {
            GameLogger.getLogger(LangManager.class).error("加载语言文件失败: " + path, e);
            return Collections.emptyMap();
        }
    }

    /**
     * 合并外部翻译文件到当前缓存（供插件等使用）。
     * 若 key 冲突，后加载的会覆盖先加载的。
     *
     * @param lang       语言代码，如 "zh_cn"
     * @param jsonStream JSON 格式翻译文件输入流（调用方负责关闭）
     */
    public static void merge(String lang, InputStream jsonStream) {
        try (Reader reader = new InputStreamReader(jsonStream, StandardCharsets.UTF_8)) {
            Map<String, String> newEntries = gson.fromJson(reader, MAP_TYPE);
            if (newEntries != null && !newEntries.isEmpty()) {
                cache.computeIfAbsent(lang, k -> new ConcurrentHashMap<>()).putAll(newEntries);
            }
        } catch (JsonSyntaxException e) {
            GameLogger.getLogger(LangManager.class).error("合并翻译文件语法错误: lang=" + lang, e);
        } catch (Exception e) {
            GameLogger.getLogger(LangManager.class).error("合并翻译文件失败: lang=" + lang, e);
        }
    }

    public static void reload() {
        cache.clear();
    }
}
