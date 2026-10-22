package com.library.dto;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Autocomplete prefix suggestions response DTO.
 */
public class SuggestResponseDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private String prefix;
    private List<String> suggestions;

    public SuggestResponseDto() {
        this.suggestions = Collections.emptyList();
    }

    public SuggestResponseDto(String prefix, List<String> suggestions) {
        this.prefix = prefix;
        this.suggestions = suggestions != null ? suggestions : Collections.emptyList();
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions;
    }
}
