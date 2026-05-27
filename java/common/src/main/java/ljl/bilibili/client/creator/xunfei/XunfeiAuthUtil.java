package ljl.bilibili.client.creator.xunfei;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class XunfeiAuthUtil {
    private XunfeiAuthUtil() {
    }

    public static boolean isConfigured(String appId, String apiKey, String apiSecret) {
        return appId != null && !appId.isEmpty() && !"xxx".equals(appId)
                && apiKey != null && !apiKey.isEmpty() && !"xxx".equals(apiKey)
                && apiSecret != null && !apiSecret.isEmpty() && !"xxx".equals(apiSecret);
    }

    public static String buildWsAuthUrl(String hostUrl, String apiKey, String apiSecret) throws Exception {
        URI uri = new URI(hostUrl);
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = format.format(new Date());
        String preStr = "host: " + uri.getHost() + "\n" + "date: " + date + "\n" + "GET " + uri.getPath() + " HTTP/1.1";
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec spec = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(spec);
        byte[] hexDigits = mac.doFinal(preStr.getBytes(StandardCharsets.UTF_8));
        String sha = Base64.getEncoder().encodeToString(hexDigits);
        String authorization = String.format(
                "api_key=\"%s\", algorithm=\"%s\", headers=\"%s\", signature=\"%s\"",
                apiKey, "hmac-sha256", "host date request-line", sha);
        return "wss://" + uri.getHost() + uri.getPath() + "?" + String.format(
                "authorization=%s&date=%s&host=%s",
                URLEncoder.encode(Base64.getEncoder().encodeToString(authorization.getBytes(StandardCharsets.UTF_8)), "UTF-8"),
                URLEncoder.encode(date, "UTF-8"),
                URLEncoder.encode(uri.getHost(), "UTF-8"));
    }
}
