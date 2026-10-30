package com.ceos24.autedi.domain.worksheet.repository;

import com.ceos24.autedi.domain.worksheet.entity.CorrectionArea;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetSetting;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorksheetSettingRepository extends JpaRepository<WorksheetSetting, Long> {

    List<WorksheetSetting> findAllByWorksheetId(Long worksheetId);

    Optional<WorksheetSetting> findByWorksheetIdAndArea(Long worksheetId, CorrectionArea area);

    void deleteAllByWorksheetId(Long worksheetId);
}
