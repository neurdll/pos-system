package com.suckcc.pos.report;

import com.suckcc.pos.shift.CashShift;
import com.suckcc.pos.shift.ShiftService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/reports")
public class ReportController {

	private final ReportService reportService;
	private final ShiftService shiftService;

	public ReportController(ReportService reportService, ShiftService shiftService) {
		this.reportService = reportService;
		this.shiftService = shiftService;
	}

	@GetMapping
	public String index(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) Long shiftId,
			@RequestParam(defaultValue = "5") int lowStockThreshold,
			@RequestHeader(value = "HX-Target", required = false) String hxTarget,
			Model model
	) {
		LocalDate targetDate = date != null ? date : LocalDate.now();
		List<CashShift> recentShifts = shiftService.recentShifts();
		Long selectedShiftId = shiftId != null
				? shiftId
				: (recentShifts.isEmpty() ? null : recentShifts.getFirst().getId());

		model.addAttribute("selectedDate", targetDate);
		model.addAttribute("lowStockThreshold", lowStockThreshold);
		model.addAttribute("recentShifts", recentShifts);
		model.addAttribute("selectedShiftId", selectedShiftId);
		model.addAttribute("daily", reportService.dailyReport(targetDate, lowStockThreshold));
		model.addAttribute("shiftReport", selectedShiftId != null ? reportService.shiftReport(selectedShiftId) : null);

		if ("report-content".equals(hxTarget)) {
			return "reports/_summary :: content";
		}
		return "reports/index";
	}
}
