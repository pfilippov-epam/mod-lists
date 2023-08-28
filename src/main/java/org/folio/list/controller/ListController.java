package org.folio.list.controller;

import lombok.RequiredArgsConstructor;
import org.folio.list.domain.dto.ListDTO;
import org.folio.list.domain.dto.ListRefreshDTO;
import org.folio.list.domain.dto.ListRequestDTO;
import org.folio.list.domain.dto.ListSummaryResultsDTO;
import org.folio.list.domain.dto.ListUpdateRequestDTO;
import org.folio.list.exception.ListNotFoundException;
import org.folio.list.rest.resource.ListApi;
import org.folio.list.services.ListActions;
import org.folio.list.services.ListService;
import org.folio.querytool.domain.dto.ResultsetPage;
import org.folio.spring.data.OffsetRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.springframework.util.StringUtils.hasText;

@RequiredArgsConstructor
@RestController
public class ListController implements ListApi {

  private final ListService listService;

  @Override
  public ResponseEntity<ListSummaryResultsDTO> getAllLists(List<UUID> ids, List<UUID> entityTypeIds, Integer offset,
                                                           Integer size, Boolean active, Boolean _private, String updatedAsOf
  ) {
    OffsetDateTime providedTimestamp;
    DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    // In the backend, the plus sign (+) that is received through RequestParams within the provided timestamp gets substituted with a blank space.
    providedTimestamp = !hasText(updatedAsOf) ? null : OffsetDateTime.parse(updatedAsOf.replace(' ', '+'), formatter);
    Pageable pageable = new OffsetRequest(offset, size);
    return ResponseEntity.ok(listService.getAllLists(pageable, ids, entityTypeIds, active, _private, providedTimestamp));
  }

  @Override
  public ResponseEntity<ListDTO> createList(ListRequestDTO listRequest) {
    var listDto = listService.createList(listRequest);
    return new ResponseEntity<>(listDto, HttpStatus.CREATED);
  }

  @Override
  public ResponseEntity<ListDTO> updateList(UUID id, ListUpdateRequestDTO listUpdateRequest) {
    return listService.updateList(id, listUpdateRequest)
      .map(ResponseEntity::ok)
      .orElseThrow(() -> new ListNotFoundException(id, ListActions.UPDATE));
  }

  @Override
  public ResponseEntity<ListDTO> getListById(UUID id) {
    return listService.getListById(id)
      .map(ResponseEntity::ok)
      .orElseThrow(() -> new ListNotFoundException(id, ListActions.READ));
  }

  @Override
  public ResponseEntity<ListRefreshDTO> performRefresh(UUID id) {
    return listService.performRefresh(id)
      .map(ResponseEntity::ok)
      .orElseThrow(() -> new ListNotFoundException(id, ListActions.REFRESH));
  }

  @Override
  public ResponseEntity<ResultsetPage> getListContents(UUID id, Integer offset, Integer size) {
    return listService.getListContents(id, offset, size)
      .map(ResponseEntity::ok)
      .orElseThrow(() -> new ListNotFoundException(id, ListActions.READ));
  }

  @Override
  public ResponseEntity<Void> deleteList(UUID id) {
    listService.deleteList(id);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<Void> cancelRefresh(UUID listId) {
    listService.cancelRefresh(listId);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }
}