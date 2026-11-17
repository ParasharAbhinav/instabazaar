package com.instabazaar.service;

import java.util.List;

public interface TagSuggestionService {
    List<String> suggest(String content);
}
