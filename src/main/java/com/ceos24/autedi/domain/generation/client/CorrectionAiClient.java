package com.ceos24.autedi.domain.generation.client;

import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetPage;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetSetting;
import java.util.List;

public interface CorrectionAiClient {

    List<CorrectionDraft> generate(Worksheet worksheet, List<WorksheetPage> pages, List<WorksheetSetting> settings);
}
