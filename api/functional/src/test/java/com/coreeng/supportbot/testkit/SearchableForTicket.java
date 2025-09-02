package com.coreeng.supportbot.testkit;

public interface SearchableForTicket {
    long ticketId();
    String channelId();
    MessageTs queryTs();
}
