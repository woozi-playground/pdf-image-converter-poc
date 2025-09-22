package com.poc.lab.batch.deputy.dto;

public record ChannelSystemDocument(
        String userId,
        String docId1,
        String docType1,
        String edmsId1,
        String docId2,
        String docType2,
        String edmsId2,
        String guid
) {

    public static ChannelSystemDocument defaults(AccountingSystemDocument item) {
        return new ChannelSystemDocument(item.userId(), item.docId1(), item.docType1(),
                null, item.docId2(), item.docType2(), null, null);
    }
}
