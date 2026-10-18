package com.fongmi.android.tv.api;

import android.util.Base64;

import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.HttpUrl;
import okhttp3.Response;

public class Decoder {

    private static final Pattern JS_URI = Pattern.compile("\"(\\.|\\.\\.)/(.?|.+?)\\.js\\?(.?|.+?)\"");

    public static String getJson(String url, String tag) throws Exception {
        try (Response res = OkHttp.newCall(url, tag).execute()) {
            HttpUrl httpUrl = res.request().url();
            int size = HttpUrl.parse(url).querySize();
            if (httpUrl.querySize() == size) url = httpUrl.toString();
            return verify(url, res.body().string());
        }
    }

    private static String verify(String url, String data) throws Exception {
        if (data.isEmpty()) throw new Exception();
        if (Json.isObj(data)) return fix(url, data);
        if (data.contains("**")) data = base64(data);
        if (data.startsWith("2423")) data = cbc(data.replaceAll("\\s+", ""));
        return fix(url, data);
    }

    private static String fix(String url, String data) {
        Matcher matcher = JS_URI.matcher(data);
        while (matcher.find()) data = replace(url, data, matcher.group());
        if (data.contains("../")) data = data.replace("../", UrlUtil.resolve(url, "../"));
        if (data.contains("./")) data = data.replace("./", UrlUtil.resolve(url, "./"));
        if (data.contains("__JS1__")) data = data.replace("__JS1__", "./");
        if (data.contains("__JS2__")) data = data.replace("__JS2__", "../");
        // JKTV: 部分站点返回以 "/" 开头的绝对路径（如 "/api/xxx"），
        // 若不补全 scheme+host，OkHttp 会抛
        // "Expected URL scheme 'http' or 'https' but no scheme was found"
        data = fixRootRelative(url, data);
        return data;
    }

    /**
     * 把 JSON 字符串里以 "/" 开头的相对路径补全为完整 URL。
     *
     * <p>只处理形如 {@code "https://..."} 或 {@code "/path"} 的取值：凡出现在引号内、
     * 以 / 开头且不含 "://" 的片段，都用 baseUri 的 origin 补齐。</p>
     */
    private static String fixRootRelative(String url, String data) {
        if (url == null || url.isEmpty()) return data;
        String origin = originOf(url);
        if (origin.isEmpty()) return data;
        Matcher m = Pattern.compile("\"(/[^\"\\s]*)\"").matcher(data);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            String p = m.group(1);
            // 跳过协议相对 "//" 与已是完整地址的情况
            if (p.startsWith("//") || p.contains("://")) continue;
            sb.append(data, last, m.start(1)).append(origin).append(p);
            last = m.end(1);
        }
        if (last == 0) return data;
        sb.append(data.substring(last));
        return sb.toString();
    }

    /** 取 scheme://host[:port]，失败返回空串。 */
    private static String originOf(String url) {
        try {
            int i = url.indexOf("://");
            if (i < 0) return "";
            int start = i + 3;
            int end = url.length();
            for (int k = start; k < url.length(); k++) {
                char c = url.charAt(k);
                if (c == '/' || c == '?' || c == '#') { end = k; break; }
            }
            return url.substring(0, end);
        } catch (Exception e) {
            return "";
        }
    }

    private static String replace(String url, String data, String ext) {
        String t = ext.replace("\"./", "\"" + UrlUtil.resolve(url, "./"));
        t = t.replace("\"../", "\"" + UrlUtil.resolve(url, "../"));
        t = t.replace("./", "__JS1__").replace("../", "__JS2__");
        return data.replace(ext, t);
    }

    private static String cbc(String data) throws Exception {
        String decode = new String(Util.hex2byte(data)).toLowerCase();
        String key = padEnd(decode.substring(decode.indexOf("$#") + 2, decode.indexOf("#$")));
        String iv = padEnd(decode.substring(decode.length() - 13));
        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes());
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        data = data.substring(data.indexOf("2324") + 4, data.length() - 26);
        byte[] decryptData = cipher.doFinal(Util.hex2byte(data));
        return new String(decryptData, StandardCharsets.UTF_8);
    }

    private static String base64(String data) {
        String extract = extract(data);
        if (extract.isEmpty()) return data;
        return new String(Base64.decode(extract, Base64.DEFAULT));
    }

    private static String extract(String data) {
        Matcher matcher = Pattern.compile("[A-Za-z0-9]{8}\\*\\*").matcher(data);
        return matcher.find() ? data.substring(data.indexOf(matcher.group()) + 10) : "";
    }

    private static String padEnd(String key) {
        return key + "0000000000000000".substring(key.length());
    }
}
