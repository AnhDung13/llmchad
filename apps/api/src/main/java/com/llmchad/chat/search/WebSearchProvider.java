package com.llmchad.chat.search;

import java.util.List;

/** Một nguồn tìm kiếm web mà model dùng qua tool web_search. Thêm provider mới: tạo một @Component implement interface này. */
public interface WebSearchProvider {

    /** Giá trị `searchProvider` trong request, ví dụ "tavily" */
    String id();

    /** false khi chưa đặt API key: không hiện cho người dùng chọn */
    boolean isEnabled();

    /** Ném RestClientException khi provider lỗi hoặc quá thời gian chờ */
    List<SearchResult> search(String query);
}
