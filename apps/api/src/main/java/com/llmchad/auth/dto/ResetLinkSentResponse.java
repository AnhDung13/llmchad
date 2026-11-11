package com.llmchad.auth.dto;

/** expiresIn: link sống bao lâu (giây). resendAfter: bao lâu nữa mới được gửi lại (giây) */
public record ResetLinkSentResponse(long expiresIn, long resendAfter) {
}
