package com.jones.kb.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

public class HtmlSanitizerUtil {

    private static final Safelist SAFE_LIST = Safelist.relaxed()
            .addTags("pre", "code", "blockquote", "hr", "img", "table", "thead", "tbody", "tr", "th", "td")
            .addAttributes("img", "src", "alt", "width", "height")
            .addAttributes("table", "border", "cellpadding", "cellspacing")
            .addAttributes(":all", "class", "style");

    public static String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return Jsoup.clean(html, SAFE_LIST);
    }
}
