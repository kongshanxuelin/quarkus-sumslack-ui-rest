package org.acme.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.graalvm.polyglot.HostAccess;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
@ApplicationScoped
public class HttpHelper {
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    // 私有构造，防止实例化
    private HttpHelper() {}

    // -------------------- GET --------------------

    /**
     * 发送GET请求
     *
     * @param url    请求地址
     * @param params 查询参数（可选）
     * @return 响应体字符串
     */
    @HostAccess.Export
    public String get(String url, Map<String, String> params) throws IOException, InterruptedException {
        return get(url, params, null);
    }

    /**
     * 发送GET请求（带请求头）
     *
     * @param url     请求地址
     * @param params  查询参数（可选）
     * @param headers 请求头（可选）
     * @return 响应体字符串
     */
    @HostAccess.Export
    public String get(String url, Map<String, String> params, Map<String, String> headers) throws IOException, InterruptedException {
        String fullUrl = buildUrlWithParams(url, params);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", DEFAULT_USER_AGENT)
                .GET();

        if (headers != null) {
            headers.forEach(builder::header);
        }

        HttpRequest request = builder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return response.body();
    }

    // -------------------- POST (form) --------------------

    /**
     * 发送POST表单请求（application/x-www-form-urlencoded）
     *
     * @param url    请求地址
     * @param params 表单参数
     * @return 响应体字符串
     */
    @HostAccess.Export
    public String postForm(String url, Map<String, String> params) throws IOException, InterruptedException {
        return postForm(url, params, null);
    }

    /**
     * 发送POST表单请求（带请求头）
     *
     * @param url     请求地址
     * @param params  表单参数
     * @param headers 请求头（可选）
     * @return 响应体字符串
     */
    @HostAccess.Export
    public String postForm(String url, Map<String, String> params, Map<String, String> headers) throws IOException, InterruptedException {
        String formBody = toFormUrlEncoded(params);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8));

        if (headers != null) {
            headers.forEach(builder::header);
        }

        HttpRequest request = builder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return response.body();
    }

    // -------------------- POST (json) --------------------

    /**
     * 发送POST JSON请求（application/json）
     *
     * @param url       请求地址
     * @param jsonBody  JSON字符串
     * @return 响应体字符串
     */
    @HostAccess.Export
    public String postJson(String url, String jsonBody) throws IOException, InterruptedException {
        return postJson(url, jsonBody, null);
    }

    /**
     * 发送POST JSON请求（带请求头）
     *
     * @param url       请求地址
     * @param jsonBody  JSON字符串
     * @param headers   请求头（可选）
     * @return 响应体字符串
     */
    @HostAccess.Export
    public String postJson(String url, String jsonBody, Map<String, String> headers) throws IOException, InterruptedException {
        if (jsonBody == null) {
            jsonBody = "{}";
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));

        if (headers != null) {
            headers.forEach(builder::header);
        }

        HttpRequest request = builder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return response.body();
    }

    // -------------------- PUT --------------------

    /**
     * 发送PUT JSON请求（通常用于更新资源）
     *
     * @param url       请求地址
     * @param jsonBody  JSON字符串
     * @return 响应体字符串
     */
    @HostAccess.Export
    public String putJson(String url, String jsonBody) throws IOException, InterruptedException {
        return putJson(url, jsonBody, null);
    }

    /**
     * 发送PUT JSON请求（带请求头）
     *
     * @param url       请求地址
     * @param jsonBody  JSON字符串
     * @param headers   请求头（可选）
     * @return 响应体字符串
     */
    @HostAccess.Export
    public String putJson(String url, String jsonBody, Map<String, String> headers) throws IOException, InterruptedException {
        if (jsonBody == null) {
            jsonBody = "{}";
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Content-Type", "application/json; charset=UTF-8")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));

        if (headers != null) {
            headers.forEach(builder::header);
        }

        HttpRequest request = builder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return response.body();
    }

    /**
     * 发送PUT表单请求
     *
     * @param url    请求地址
     * @param params 表单参数
     * @return 响应体字符串
     */
    @HostAccess.Export
    public String putForm(String url, Map<String, String> params) throws IOException, InterruptedException {
        return putForm(url, params, null);
    }

    /**
     * 发送PUT表单请求（带请求头）
     *
     * @param url     请求地址
     * @param params  表单参数
     * @param headers 请求头（可选）
     * @return 响应体字符串
     */
    @HostAccess.Export
    public String putForm(String url, Map<String, String> params, Map<String, String> headers) throws IOException, InterruptedException {
        String formBody = toFormUrlEncoded(params);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .PUT(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8));

        if (headers != null) {
            headers.forEach(builder::header);
        }

        HttpRequest request = builder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return response.body();
    }

    // -------------------- DELETE --------------------

    /**
     * 发送DELETE请求
     *
     * @param url 请求地址
     * @return 响应体字符串
     */
    @HostAccess.Export
    public String delete(String url) throws IOException, InterruptedException {
        return delete(url, null, null);
    }

    /**
     * 发送DELETE请求（带查询参数）
     *
     * @param url    请求地址
     * @param params 查询参数（可选）
     * @return 响应体字符串
     */
    @HostAccess.Export
    public String delete(String url, Map<String, String> params) throws IOException, InterruptedException {
        return delete(url, params, null);
    }

    /**
     * 发送DELETE请求（带查询参数和请求头）
     *
     * @param url     请求地址
     * @param params  查询参数（可选）
     * @param headers 请求头（可选）
     * @return 响应体字符串
     */
    @HostAccess.Export
    public String delete(String url, Map<String, String> params, Map<String, String> headers) throws IOException, InterruptedException {
        String fullUrl = buildUrlWithParams(url, params);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", DEFAULT_USER_AGENT)
                .DELETE();

        if (headers != null) {
            headers.forEach(builder::header);
        }

        HttpRequest request = builder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return response.body();
    }

    /**
     * 发送DELETE请求（带JSON请求体）
     *
     * @param url      请求地址
     * @param jsonBody JSON字符串
     * @return 响应体字符串
     */
    @HostAccess.Export
    public String deleteWithJson(String url, String jsonBody) throws IOException, InterruptedException {
        return deleteWithJson(url, jsonBody, null);
    }

    /**
     * 发送DELETE请求（带JSON请求体和请求头）
     *
     * @param url      请求地址
     * @param jsonBody JSON字符串
     * @param headers  请求头（可选）
     * @return 响应体字符串
     */
    @HostAccess.Export
    public String deleteWithJson(String url, String jsonBody, Map<String, String> headers) throws IOException, InterruptedException {
        if (jsonBody == null) {
            jsonBody = "{}";
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Content-Type", "application/json; charset=UTF-8")
                .method("DELETE", HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));

        if (headers != null) {
            headers.forEach(builder::header);
        }

        HttpRequest request = builder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return response.body();
    }

    // -------------------- 辅助方法 --------------------

    /**
     * 构建带查询参数的URL
     *
     * @param url    原始URL
     * @param params 查询参数
     * @return 带参数的URL
     */
    private String buildUrlWithParams(String url, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return url;
        }
        String queryString = params.entrySet().stream()
                .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                .collect(Collectors.joining("&"));
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + queryString;
    }

    /**
     * 将Map转换为application/x-www-form-urlencoded格式字符串
     *
     * @param params 表单参数
     * @return 表单字符串
     */
    private String toFormUrlEncoded(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        return params.entrySet().stream()
                .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    /**
     * URL编码（UTF-8）
     *
     * @param value 待编码字符串
     * @return 编码后的字符串
     */
    private String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    // -------------------- 测试示例 --------------------

//    public static void main(String[] args) {
//        try {
//            HttpHelper http = new HttpHelper();
//            // 示例1：GET请求
//            Map<String, String> getParams = new HashMap<>();
//            getParams.put("name", "张三");
//            getParams.put("age", "25");
//            String getResult = http.get("https://httpbin.org/get", getParams);
//            System.out.println("GET 响应: " + getResult);
//
//            // 示例2：POST表单请求
//            Map<String, String> formParams = new HashMap<>();
//            formParams.put("username", "admin");
//            formParams.put("password", "123456");
//            String postFormResult = http.postForm("https://httpbin.org/post", formParams);
//            System.out.println("POST Form 响应: " + postFormResult);
//
//            // 示例3：POST JSON请求
//            String jsonBody = "{\"name\":\"李四\",\"age\":30}";
//            String postJsonResult = http.postJson("https://httpbin.org/post", jsonBody);
//            System.out.println("POST JSON 响应: " + postJsonResult);
//
//            // 示例4：PUT JSON请求
//            String putJsonResult = http.putJson("https://httpbin.org/put", jsonBody);
//            System.out.println("PUT JSON 响应: " + putJsonResult);
//
//            // 示例5：DELETE请求
//            String deleteResult = http.delete("https://httpbin.org/delete");
//            System.out.println("DELETE 响应: " + deleteResult);
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
}
