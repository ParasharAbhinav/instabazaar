package com.instabazaar.search.service;

import com.instabazaar.search.repository.SearchDocumentRepository;
import com.instabazaar.shared.repository.EntityAccess;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class SearchDocumentService {
  private final SearchDocumentRepository repository;
  private final EntityAccess jpa;

  public SearchDocumentService(SearchDocumentRepository repository, EntityAccess jpa) {
    this.repository = repository;
    this.jpa = jpa;
  }

  public void product(UUID id) {
    jpa.flush();
    repository.product(id);
  }

  public void store(UUID id) {
    jpa.flush();
    repository.store(id);
  }

  public void category(UUID id) {
    jpa.flush();
    repository.category(id);
  }
}
