package bes.tcc.apiauthtest.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface RequestUtils {

    public static final Pattern PATH_PARAMETER = Pattern.compile("\\{([^/{}]+)\\}");
    
    public static String replacePathParameters(String endpointPath, Map<String, String> pathParameters) {
		
        Matcher matcher = PATH_PARAMETER.matcher(endpointPath);
		StringBuilder resolvedPath = new StringBuilder();
		
        while (matcher.find()) {

			String parameterName = matcher.group(1);
			String value = pathParameters == null ? null : pathParameters.get(parameterName);
			
            if (value == null) {
				throw new IllegalArgumentException("Missing path parameter: " + parameterName);
			}
			
            matcher.appendReplacement(resolvedPath, Matcher.quoteReplacement(encode(value)));
		
        }
		
        matcher.appendTail(resolvedPath);
		
        return resolvedPath.toString();
	
    }
    
    public static String joinUrl(String baseUrl, String endpointPath) {
		return baseUrl.replaceAll("/+$", "") + "/" + endpointPath.replaceAll("^/+", "");
	}

    public static String appendQueryParameters(String requestUrl, Map<String, String> queryParameters) {
		
        if (queryParameters == null || queryParameters.isEmpty()) {
			return requestUrl;
		}

		StringBuilder url = new StringBuilder(requestUrl);
		url.append(requestUrl.contains("?") ? "&" : "?");
		
        queryParameters.forEach((name, value) -> {
			if (url.charAt(url.length() - 1) != '?' && url.charAt(url.length() - 1) != '&') {
				url.append('&');
			}
			url.append(encode(name)).append('=').append(encode(value));
		});
		
        return url.toString();
	
    }

    private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

}
