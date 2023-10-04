package org.folio.list.services;

import lombok.RequiredArgsConstructor;
import org.folio.fqm.lib.service.FqlValidationService;
import org.folio.list.domain.AsyncProcessStatus;
import org.folio.list.domain.ExportDetails;
import org.folio.list.domain.ListEntity;
import org.folio.list.domain.dto.ListRequestDTO;
import org.folio.list.domain.dto.ListUpdateRequestDTO;
import org.folio.list.exception.*;
import org.folio.list.repository.ListExportRepository;

import org.folio.querytool.domain.dto.EntityType;
import org.folio.spring.FolioExecutionContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.UUID;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.folio.list.exception.ExportNotFoundException.inProgressExportNotFound;
import static org.folio.list.services.ListActions.*;

@Service
@RequiredArgsConstructor
public class ListValidationService {
  private final FolioExecutionContext folioExecutionContext;
  private final FqlValidationService fqlValidationService;
  private final ListExportRepository listExportRepository;

  public void validateCreate(ListRequestDTO saveRequest, EntityType entityType) {
    assertIsValidFql(entityType, saveRequest.getFqlQuery(), CREATE);
  }

  public void validateUpdate(ListEntity list, ListUpdateRequestDTO updateRequest, EntityType entityType) {
    assertSharedOrOwnedByUser(list, UPDATE);
    assertListNotCanned(list, UPDATE);
    assertListNotRefreshing(list, UPDATE);
    assertListVersionMatched(list, updateRequest.getVersion());
    assertIsValidFql(entityType, updateRequest.getFqlQuery(), UPDATE);
    assertListNotExporting(list, UPDATE);
  }

  public void validateDelete(ListEntity list) {
    assertSharedOrOwnedByUser(list, DELETE);
    assertListNotCanned(list, DELETE);
    assertListNotRefreshing(list, DELETE);
    assertListNotExporting(list, DELETE);
  }

  public void validateRefresh(ListEntity list) {
    assertSharedOrOwnedByUser(list, REFRESH);
    assertListIsActive(list, REFRESH);
    assertListHasQuery(list, REFRESH);
    assertListNotRefreshing(list, REFRESH);
    assertListNotExporting(list, REFRESH);
  }

  public void validateCancelRefresh(ListEntity list) {
    assertSharedOrOwnedByUser(list, CANCEL_REFRESH);
    assertRefreshInProgress(list, CANCEL_REFRESH);
  }

  public void assertSharedOrOwnedByUser(ListEntity list, ListActions failedAction) {
    UUID currentOwnerId = (list.getUpdatedBy() == null) ? list.getCreatedBy() : list.getUpdatedBy();
    if (Boolean.TRUE.equals(list.getIsPrivate()) && ! currentOwnerId.equals(folioExecutionContext.getUserId())) {
      throw new PrivateListOfAnotherUserException(list, failedAction);
    }
  }

  public void validateCreateExport(ListEntity list) {
    assertListIsActive(list, EXPORT);
    assertSharedOrOwnedByUser(list, EXPORT);
    assertListNotRefreshing(list, EXPORT);
    assertUserNotExporting(list, EXPORT);
  }

  public void validateGetExport(ListEntity list) {
    assertSharedOrOwnedByUser(list, EXPORT);
  }

  public void validateDownloadExport(ListEntity list) {
    assertSharedOrOwnedByUser(list, EXPORT);
  }

  public void validateCancelExport(ExportDetails exportDetails) {
    assertSharedOrOwnedByUser(exportDetails.getList(), CANCEL_EXPORT);
    assertExportInProgress(exportDetails, CANCEL_EXPORT);
  }

  private void assertListIsActive(ListEntity list, ListActions failedAction) {
    if (Boolean.FALSE.equals(list.getIsActive())) {
      throw new ListInactiveException(list, failedAction);
    }
  }

  private void assertListNotCanned(ListEntity list, ListActions failedAction) {
    if (Boolean.TRUE.equals(list.getIsCanned())) {
      throw new ListIsCannedException(list, failedAction);
    }
  }

  private void assertRefreshInProgress(ListEntity list, ListActions failedAction) {
    if (!list.isRefreshing()) {
      throw new ListNotRefreshingException(list, failedAction);
    }
  }

  private void assertListNotRefreshing(ListEntity list, ListActions failedAction) {
    if (list.isRefreshing()) {
      throw new RefreshInProgressException(list, failedAction);
    }
  }

  private void assertListHasQuery(ListEntity list, ListActions failedAction) {
    if (!StringUtils.hasText(list.getFqlQuery())) {
      throw new MissingQueryException(list, failedAction);
    }
  }

  private void assertListVersionMatched(ListEntity list, int versionInRequest) {
    if (list.getVersion() != versionInRequest) {
      throw new OptimisticLockException(list, versionInRequest);
    }
  }

  private void assertIsValidFql(EntityType entityType, String fqlQuery, ListActions failedAction) {
    // Lists can be created without an FQL query. Validate FQL if the query is not empty
    if (isNotEmpty(fqlQuery)) {
      Map<String, String> errorMap = fqlValidationService.validateFql(entityType, fqlQuery);
      if (!errorMap.isEmpty()) {
        throw new InvalidFqlException(fqlQuery, failedAction, errorMap);
      }
    }
  }

  private void assertListNotExporting(ListEntity list, ListActions failedAction) {
    if (listExportRepository.isExporting(list.getId())) {
      throw new ExportInProgressException(list, failedAction);
    }
  }

  private void assertExportInProgress(ExportDetails exportDetails, ListActions failedAction) {
    if (exportDetails.getStatus() != AsyncProcessStatus.IN_PROGRESS) {
      throw inProgressExportNotFound(exportDetails.getList().getId(), exportDetails.getExportId(), failedAction);
    }
  }

  private void assertUserNotExporting(ListEntity list, ListActions failedAction) {
    if (listExportRepository.isUserAlreadyExporting(list.getId(), folioExecutionContext.getUserId())) {
      throw new ExportInProgressException(list, failedAction);
    }
  }
}
