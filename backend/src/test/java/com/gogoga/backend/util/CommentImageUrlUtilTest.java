package com.gogoga.backend.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommentImageUrlUtilTest {

    @Test
    void parseImageUrlsTrimsDeduplicatesAndLimits() {
        List<String> urls = CommentImageUrlUtil.parseImageUrls(
                " https://a.com/1.jpg,invalid,https://a.com/1.jpg\nhttp://b.com/2.png https://c.com/3.webp ",
                2
        );

        assertEquals(List.of("https://a.com/1.jpg", "http://b.com/2.png"), urls);
    }

    @Test
    void parseImageUrlsFromListFiltersInvalidValues() {
        List<String> urls = CommentImageUrlUtil.parseImageUrls(
                List.of("", "ftp://a.com/1.jpg", "https://a.com/1.jpg"),
                3
        );

        assertEquals(List.of("https://a.com/1.jpg"), urls);
    }
}
