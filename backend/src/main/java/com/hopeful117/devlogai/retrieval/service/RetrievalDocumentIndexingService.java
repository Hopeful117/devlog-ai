package com.hopeful117.devlogai.retrieval.service;

import com.hopeful117.devlogai.retrieval.contract.IndexableDocument;

public interface RetrievalDocumentIndexingService {

    IndexingResult index(IndexableDocument document);
}
