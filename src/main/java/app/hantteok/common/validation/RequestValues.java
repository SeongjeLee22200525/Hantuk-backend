package app.hantteok.common.validation;

import app.hantteok.common.error.ApiException;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Shared input parsing. Length limits and API error messages remain unchanged. */
public final class RequestValues {
    private static final Pattern GRAPHEME = Pattern.compile("\\X");

    private RequestValues() {}

    public static String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public static String text(Map<String, Object> data, String name, int max, boolean required) {
        Object supplied = data.get(name);
        if (supplied != null && !(supplied instanceof String)) {
            throw ApiException.bad(name + " 형식이 올바르지 않습니다.");
        }
        String value = supplied == null ? "" : ((String) supplied).trim();
        if ((required && value.isEmpty()) || value.length() > max) {
            throw ApiException.bad(name + " 길이를 확인해 주세요.");
        }
        return value;
    }

    public static String graphemeText(
            Map<String, Object> data, String name, int max, boolean required) {
        String value = text(data, name, Integer.MAX_VALUE, required);
        if (GRAPHEME.matcher(value).results().limit(max + 1L).count() > max) {
            throw ApiException.bad(name + " 길이를 확인해 주세요.");
        }
        return value;
    }

    public static LocalDate date(String value) {
        try {
            var d = LocalDate.parse(value);
            if (d.getYear() < 1 || d.getYear() > 9999) {
                throw new IllegalArgumentException();
            }
            return d;
        } catch (Exception e) {
            throw ApiException.bad("올바른 날짜를 입력해 주세요.");
        }
    }
}
