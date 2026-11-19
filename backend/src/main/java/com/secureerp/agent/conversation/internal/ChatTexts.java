package com.secureerp.agent.conversation.internal;

/**
 * Fixed customer-facing texts for turns without a model answer. They never say why (A-14: no reason to
 * the customer; reasons are internal codes for staff).
 */
final class ChatTexts {

    static final String HANDED_OFF =
            "Yêu cầu của bạn đã được chuyển cho nhân viên tư vấn. Nhân viên sẽ phản hồi bạn sớm.";
    static final String WAITING_FOR_STAFF =
            "Cuộc trò chuyện đang chờ nhân viên tư vấn. Nhân viên sẽ phản hồi bạn sớm.";
    static final String VIN_AGAIN =
            "Số VIN chưa hợp lệ hoặc có nhiều hơn một số VIN. Vui lòng gửi lại đúng một số VIN gồm 17 ký tự.";
    static final String BLOCKED_PLACEHOLDER = "[tin nhắn đã bị chặn]";

    private ChatTexts() {
    }
}
