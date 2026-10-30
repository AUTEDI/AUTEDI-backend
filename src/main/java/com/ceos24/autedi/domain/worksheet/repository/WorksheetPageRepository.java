package com.ceos24.autedi.domain.worksheet.repository;

import com.ceos24.autedi.domain.worksheet.entity.WorksheetPage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorksheetPageRepository extends JpaRepository<WorksheetPage, Long> {

    List<WorksheetPage> findAllByWorksheetIdOrderByPageNumberAsc(Long worksheetId);

    Optional<WorksheetPage> findByWorksheetIdAndPageNumber(Long worksheetId, Integer pageNumber);

    Optional<WorksheetPage> findByIdAndWorksheetId(Long id, Long worksheetId);

    void deleteAllByWorksheetId(Long worksheetId);
}
